# FFM Native Layer: Replacing Java Glide with Direct Rust Interop

## Executive Summary

Valkey4Cats currently wraps the **Java Glide client** (`io.valkey:valkey-glide`), which itself is a JNI bridge to the **Rust glide-core** library. This creates a three-layer stack: Scala -> Java -> Rust. This document analyzes the tradeoffs of removing the Java middle layer entirely and calling the Rust native library directly using JDK 22+ Foreign Function & Memory (FFM/Panama).

---

## Current Architecture

```
 Valkey4Cats (Scala 3 / Cats Effect)
        |
        | CompletableFuture lifting, null handling, builder unwrapping
        v
 valkey-glide Java client (39MB uber jar)
        |
        | JNI via GlideNativeBridge (protobuf serialization per command)
        v
 libglide_rs.so / .dylib (Rust)
        |
        | RESP3, connection pooling, cluster topology, pipelining, failover
        v
 Valkey Server
```

### What Each Layer Does

**Rust core (`libglide_rs`)** handles all protocol and operational complexity:
- RESP2/RESP3 protocol parsing (vendored redis-rs fork)
- Connection multiplexing with automatic reconnection (exponential backoff)
- Cluster topology consensus (majority-voting, MOVED/ASK redirect handling)
- Request pipelining (slot-grouped parallel dispatch)
- Inflight request limiting (default 1000 concurrent)
- Read routing strategies (Primary, PreferReplica, AZAffinity)
- Client-side caching (LRU/LFU)
- LZ4/ZSTD compression
- OpenTelemetry metrics

**Java layer** (`glide.api.*`) adds JVM-side ergonomics:
- `CompletableFuture<T>` wrapping for async results
- `GlideString` binary-safe string type
- Command argument builders (`SetOptions`, `ZAddOptions`, etc.)
- 29 typed command interfaces
- `BaseBatch` mutable pipeline builder
- Protobuf serialization/deserialization for command dispatch
- Response type coercion with unchecked casts
- `ClusterValue<T>` single-vs-multi-node response wrapper

**Valkey4Cats** (our layer) then wraps Java to be idiomatic Scala:
- `F[ValkeyResponse[A]]` effect wrapping
- Codec-based key/value encoding/decoding
- ADT arguments replacing mutable builders
- Null -> Option mapping
- `CompletableFuture` -> `Async[F]` lifting

### The JNI Bridge Interface

The Rust library exposes a narrow JNI surface via `GlideNativeBridge`:

| Method | Purpose |
|--------|---------|
| `createClient(byte[] configProto, AddressResolver)` | Initialize connection, returns handle |
| `closeClient(long handle)` | Destroy connection |
| `executeCommandAsync(long handle, byte[] commandProto, long callbackId)` | Execute single command |
| `executeBinaryCommandAsync(long handle, byte[] commandProto, long callbackId)` | Binary-safe variant |
| `executeBatchAsync(long handle, byte[] batchProto, boolean utf8, long callbackId)` | Pipeline/transaction |
| `executeScriptAsync(long handle, long callbackId, String hash, byte[][] keys, byte[][] args)` | Server functions |
| `markTimedOut(long callbackId)` | Timeout signaling |
| `isConnected(long handle)` | Health check |

Commands are serialized as **protobuf** (`CommandRequest`) on the Java side, deserialized in Rust. Responses flow back via dedicated JNI callback worker threads (default 2) that complete the registered `CompletableFuture`.

---

## Proposed Architecture: FFM Direct

```
 Valkey4Cats (Scala 3 / Cats Effect)
        |
        | FFM MethodHandle calls, Arena-managed memory
        v
 libglide_rs.so / .dylib (Rust, with new C ABI exports)
        |
        | RESP3, connection pooling, cluster topology, pipelining, failover
        v
 Valkey Server
```

### What This Requires

1. **New C ABI layer in Rust** — The Rust library currently exports JNI symbols (`Java_glide_ffi_*`). We would need `extern "C"` functions with `#[no_mangle]` that expose the same command execution interface but using C-compatible types instead of JNI types.

2. **FFM bindings in Scala** — Using `java.lang.foreign.*` to call those C functions directly. MemorySegments for buffer passing, Arena lifecycle for memory management, upcall stubs for async response callbacks.

3. **Eliminate protobuf entirely** — The current Java client serializes every command to protobuf before passing it over JNI. This exists because the Rust core uses a single `protobuf_bridge` code path shared with the Python/Node UDS clients — not because in-process calls require it. A direct C ABI can pass `(command_enum, arg_pointers, arg_lengths)` with zero serialization:

   ```c
   int64_t glide_execute(
       void* client,
       uint16_t command_type,
       const uint8_t** args, const size_t* arg_lens, size_t arg_count,
       void* callback_ctx
   );
   ```

   This eliminates per-command protobuf construction, `.toByteArray()` serialization, JNI byte array copy, and Rust-side deserialization — all pure overhead for an in-process call.

---

## Tradeoff Analysis

### What We Gain

#### 1. Eliminate the 39MB Java Dependency

The uber jar is 39MB of compiled Java bytecode + native binaries for 7 platforms. With FFM, we only ship the platform-specific `libglide_rs` binary (~7-8MB) relevant to the deployment target, plus our Scala code.

**Impact**: Faster CI, smaller container images, no version conflicts with other Glide users on the classpath.

#### 2. Remove Java Impedance Mismatch

The current Valkey4Cats code is ~40% boilerplate mapping Java idioms to Scala:

| Java Pattern | Scala Mapping Cost |
|---|---|
| `CompletableFuture<T>` | `Async[F].fromCompletableFuture(IO.delay(...))` |
| Nullable returns | `Option(result).map(...)` per call |
| `Object[]` batch results | Indexed positional casting |
| Mutable builders | ADT construction + `.toGlide` conversion |
| `GlideString` | Custom `Codec` encode/decode at every boundary |
| `@SuppressWarnings("unchecked")` | Runtime ClassCastException risk |
| Java HashMap results | `.asScala.toMap` conversions |

With FFM, we own the entire Scala-to-native boundary. We can design it to be zero-mapping: Scala ADTs serialize directly to the wire format, responses decode directly to Scala types.

#### 3. True Zero-Copy for Large Values

FFM `MemorySegment` can represent response buffers without copying into JVM heap arrays. For bulk operations (large `MGET` results, streams, scan iterations), this avoids:
- Rust -> JNI byte[] copy
- JNI byte[] -> GlideString copy
- GlideString.getBytes() -> our decode copy

**Potential**: Read response buffers as `MemorySegment` slices, decode lazily.

#### 4. Better Async Model

Current flow:
1. Scala `IO` dispatches work
2. Java creates `CompletableFuture`, registers callback ID
3. JNI calls into Rust
4. Rust completes work, invokes JNI callback on worker thread
5. Java completes the `CompletableFuture`
6. Scala lifts the future into `IO`

With FFM, we can design the callback mechanism to integrate directly with Cats Effect:
- Upcall stub that directly completes a `Deferred[F, A]`
- Or: poll-based approach using a native ring buffer (avoid upcall overhead entirely)
- Or: direct `IO.async_` with a native completion token

#### 5. Cancellation Propagation

Currently, cancelling an `IO` fiber does **not** cancel the underlying Valkey command — the `CompletableFuture` will still complete, and the JNI callback will still fire. With a custom native layer, we could implement:
- Inflight request tracking on the Scala side
- Cancel signals to the Rust layer via `markTimedOut` or similar
- True fiber cancellation semantics for long-running commands (BLPOP, XREAD BLOCK)

#### 6. Version Decoupling from Java Client Releases

We currently depend on `io.valkey:valkey-glide:2.4.0`. If Glide changes Java API surface (renames, removes methods, changes types), we must adapt. By depending only on the Rust library's C ABI (which is more stable by nature), we decouple from Java-specific churn.

#### 7. Smaller Attack Surface

Removing 39MB of Java bytecode from the dependency graph removes potential supply-chain risk, reduces CVE exposure surface, and simplifies license compliance (one less Maven artifact to audit).

---

### What We Lose

#### 1. Maintenance Burden of the FFI Layer

The Java client team maintains the JNI bridge, protobuf schemas, and handles platform-specific linking issues. By going direct, we own:
- C ABI header maintenance (must match Rust library version)
- Platform-specific library loading
- Memory safety at the boundary (arenas must be closed correctly)
- Protobuf schema synchronization or custom protocol maintenance

**Estimated effort**: Significant upfront (2-4 months), ongoing maintenance (~20% of development time).

#### 2. ~~No Upstream C ABI Exists Yet~~ — RESOLVED

The `/ffi/` crate in the Glide monorepo **already exports a stable C ABI** used by the Go client. It provides:

- `create_client`, `command`, `command_with_buffer`, `close_client`, `batch`
- `store_script`, `drop_script`
- `free_command_response`, `free_connection_response`, `free_response_arena`
- Pub/sub callback registration (`register_pubsub_callback`, `unregister_pubsub_callback`)
- OpenTelemetry span management
- Logging (`init`, `glide_log`)

The header is auto-generated via `cbindgen`. Commands are passed as `(command_type_enum, byte_arg_pointers, arg_count)` — no per-command protobuf. Only connection config uses protobuf serialization.

**This means no fork is needed.** We link against the same `libglide_ffi` that Go uses.

#### 3. JDK 22+ Requirement

FFM is finalized in JDK 22. This cuts off users on JDK 17 LTS or JDK 21 LTS. The Scala/Typelevel ecosystem still largely targets JDK 11/17/21.

**Mitigation**: Could offer both — FFM backend on JDK 22+, Java Glide backend on JDK 17+. But this doubles the maintenance surface.

#### 4. Confined Arena + Fiber Scheduling Tension

Cats Effect schedules fibers across threads. FFM confined arenas are single-thread-only. Options:
- Use shared arenas everywhere (slight overhead for thread-safety checks)
- Pin arena operations to a single `IO { ... }` block (limits composability)
- Use `IO.evalOn(singleThread)` for arena access (adds scheduling overhead)

This is solvable but adds design complexity.

#### 5. Upcall Limitations for Async Responses

When Rust completes a command asynchronously and needs to deliver the result back to Scala, it must call an upcall stub. Upcalls:
- Execute on the native thread (not a CE worker thread)
- Add ~50-100ns overhead per callback
- Require the upcall arena to remain open for the lifetime of the native callback registration
- Cannot directly interact with `IO` (must use thread-safe primitives like `Deferred`)

Not a dealbreaker, but the design requires care.

#### 6. Loss of Feature Parity with Upstream

The Java client gets new command support, cluster improvements, and bug fixes from the Glide team. If we bypass it, we must:
- Track glide-core releases for new command support
- Update our C ABI layer when new commands are added
- Test against new Valkey server versions ourselves

#### 7. Debugging Complexity

JNI crashes produce `hs_err_pid` files with native stack traces. FFM access violations produce `IllegalStateException` or `IndexOutOfBoundsException` — more debuggable than JNI segfaults, but native-side crashes are still opaque without native debugger attachment.

---

### Alternative: UDS + Protobuf (The Python/Node Path)

Instead of FFM direct calls, we could use the **same IPC mechanism** that Python and Node Glide clients use:

```
 Valkey4Cats (Scala 3 / Cats Effect)
        |
        | Unix Domain Socket + Protobuf frames (or TCP loopback)
        v
 glide-core socket_listener (Rust binary, spawned as subprocess)
        |
        | RESP3, pooling, topology, etc.
        v
 Valkey Server
```

**Advantages over FFM direct**:
- No C ABI needed — the socket protocol already exists and is maintained upstream
- Process isolation (Rust crash doesn't take down JVM)
- No JDK version requirement
- No fork needed

**Disadvantages**:
- IPC overhead (~10-50us per command vs ~1-5us for in-process)
- Subprocess lifecycle management
- Protobuf ser/de on every command (same as current Java path)
- Harder to do zero-copy

---

## Value Proposition Matrix

| Stakeholder | FFM Direct Value | Risk |
|---|---|---|
| **Library users** | Smaller JAR, faster startup, no Java Glide version conflicts | JDK 22+ requirement limits adoption |
| **Performance-sensitive apps** | Zero-copy responses, no intermediate allocations, tighter async integration | Marginal for most workloads (Valkey latency dominates) |
| **Valkey4Cats maintainers** | Full control over boundary, cleaner code, no Java mapping boilerplate | Own the FFI layer permanently, must track Rust releases |
| **Ecosystem** | First Scala FFM-based database client; demonstrates pattern for other projects | Pioneering carries discovery cost |
| **Upstream Glide team** | Potential contribution of a C ABI layer benefits all non-JNI wrappers | Requires their buy-in and cooperation |

---

## Recommended Path

### Phase 1: Validate (2-4 weeks)

1. **Prototype FFM bindings against `libglide_ffi`** — The C ABI already exists (used by Go). Use `cbindgen`-generated `lib.h` with `jextract` to produce FFM bindings. Implement `create_client` + `command` + `close_client` for a minimal GET/SET proof of concept.

2. **Solve the callback model** — Go uses `successCallback`/`failureCallback` function pointers registered at client creation. Implement these as FFM upcall stubs that complete a `Deferred[IO, *]` or write to a `cats.effect.std.Queue`.

3. **Benchmark** — Compare: current Java path (protobuf ser/de + JNI + CompletableFuture lifting) vs FFM direct (raw args + upcall). Measure p50/p99 latency and allocation rate on a GET/SET workload.

### Phase 2: Build FFM Layer (if justified, 2-3 months)

Only proceed if Phase 1 shows:
- UDS overhead is acceptable OR upstream accepts C ABI contribution
- Performance difference vs Java path is meaningful (>20% p99 improvement)
- User demand exists for JDK 22+ target

If proceeding:
1. Define C ABI surface (matching the ~10 JNI methods above)
2. Use `cbindgen` to generate C header from Rust
3. Use `jextract` to generate Java/Scala FFM bindings
4. Implement Arena-based lifecycle tied to `Resource[F, *]`
5. Design async callback mechanism (likely ring buffer + polling, not upcalls)

### Phase 3: Dual Backend (ongoing)

Ship both backends behind a sealed trait:
```scala
sealed trait ValkeyBackend
object ValkeyBackend:
  case object JavaGlide extends ValkeyBackend   // JDK 17+, current implementation
  case object NativeFFM extends ValkeyBackend   // JDK 22+, zero-copy, smaller footprint
  case object SocketIPC extends ValkeyBackend   // JDK 17+, no Java dep, subprocess
```

Users choose at construction time. The algebra layer (`ValkeyCommands[F, K, V]`) remains identical.

---

## Decision Criteria

Proceed with FFM if **all** of:
- [x] ~~Upstream Glide team has C ABI layer~~ — **exists in `/ffi/` crate, used by Go client**
- [ ] Benchmarks show >20% improvement on p99 latency for pipeline-heavy workloads
- [ ] Target user base can adopt JDK 22+ (or we commit to dual-backend)
- [ ] We have bandwidth for 2-3 months of FFI layer development

Stay with Java Glide if **any** of:
- [ ] Performance difference is <10% (network latency dominates)
- [ ] JDK 17/21 LTS support is required without dual-backend
- [ ] Development bandwidth is better spent on feature coverage (Pub/Sub, Transactions, JSON)

---

## Appendix: Technical Details

### FFM Arena <-> Cats Effect Resource Mapping

```scala
import cats.effect.{IO, Resource}
import java.lang.foreign.{Arena, MemorySegment}

val arena: Resource[IO, Arena] =
  Resource.fromAutoCloseable(IO(Arena.ofShared()))

def executeCommand(arena: Arena, cmd: MemorySegment): IO[MemorySegment] =
  IO.async_ { cb =>
    // Register callback, invoke native async command
    // Native side calls upcall when done, which triggers cb(Right(result))
  }
```

### Current Protobuf Overhead (Per Command)

The Java path pays this cost for every single command, even though it's an in-process call:

```
Java side:                             Rust side:
  1. Allocate CommandRequest builder     5. Copy byte[] from JNI into Rust Vec
  2. Set fields (type, args)             6. protobuf::parse(bytes)
  3. .build() (validate + freeze)        7. Convert to internal Command struct
  4. .toByteArray() (serialize)          8. Execute
```

Steps 1-7 are eliminated entirely with a direct C ABI. The command enum and argument pointers are passed in registers/stack — no heap allocation, no serialization, no deserialization.

```protobuf
// What's currently serialized per-command (from glide-core/src/protobuf/command_request.proto):
message CommandRequest {
  uint32 callback_idx = 1;
  RequestType request_type = 2;
  repeated bytes args = 3;
}
```

### JDK Version Compatibility Matrix

| JDK | FFM Status | Scala 3.8.x Support |
|-----|-----------|-------------------|
| 17 LTS | Not available | Yes |
| 21 LTS | Preview (JEP 442) | Yes |
| 22 | Finalized (JEP 454) | Yes |
| 23+ | Stable | Yes |

### Estimated Binary Sizes

| Artifact | Current (Java Glide) | FFM Direct | UDS Path |
|----------|---------------------|-----------|----------|
| valkey4cats-core JAR | ~200KB | ~200KB | ~200KB |
| valkey4cats-effects JAR | ~150KB | ~150KB | ~150KB |
| Native dependency | 39MB (uber) or 7-8MB (platform) | 7-8MB (platform) | 7-8MB (standalone binary) |
| Total per platform | ~8.3MB | ~8.3MB | ~8.3MB |
| JDK requirement | 17+ | 22+ | 17+ |
