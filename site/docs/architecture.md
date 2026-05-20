---
layout: docs
title:  "Architecture"
number: 10
position: 10
---

# Architecture & Design Decisions

This document covers the high-level architecture of Valkey4Cats and the reasoning behind key design choices -- from module layout down to memory management in the native FFI layer.

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────┐
│  User Code                                               │
│  valkey.get("key")  → F[ValkeyResponse[Option[V]]]      │
└────────────────────────────┬─────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────┐
│  Algebra Layer (core module)                             │
│  StringCommands[F, K, V] / KeyCommands / HashCommands…   │
│  Composed into ValkeyCommands[F, K, V]                   │
└────────────────────────────┬─────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────┐
│  Implementation (effects module)                         │
│  BaseValkeyCommands → CommandDispatcher → CallbackRegistry│
└────────────────────────────┬─────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────┐
│  FFI Layer                                               │
│  GlideFfi (downcalls) ↔ libglide_ffi.dylib (Rust)       │
│  ResponseParser (MemorySegment → Scala types)            │
└──────────────────────────────────────────────────────────┘
```

## Module Layout

| Module | Purpose |
|--------|---------|
| `core` | Command algebras, error types, codecs, config models. No runtime dependencies on FFI or effect libraries beyond cats-core. |
| `effects` | Concrete implementation using JDK 22+ Foreign Function & Memory API to call `libglide_ffi` directly. |
| `log4cats` | Optional integration deriving `Log[F]` from a log4cats `Logger[F]`. |
| `streams` (planned) | fs2-based continuous consumption over Valkey Streams. |

### Why a single implementation module?

Unlike redis4cats which wraps the Lettuce Java client, Valkey4Cats calls the native Rust library directly via FFM. There is no intermediate Java client. This eliminates:

- Protobuf serialization/deserialization overhead
- JNI byte-array copies
- `CompletableFuture` allocations
- `GlideString` wrapping/unwrapping

The tradeoff is that we parse the native `CommandResponse` struct (96 bytes, `#[repr(C)]`) ourselves -- we are the first consumer to do this from outside Rust.

---

## Core Design Decisions

### 1. ValkeyResponse[A] over raw F[A]

**Decision:** Every command returns `F[ValkeyResponse[A]]` instead of `F[A]`.

**Why:** Valkey server errors (WRONGTYPE, OOM, READONLY, CROSSSLOT) are domain-level information, not infrastructure failures. Throwing them as exceptions conflates "the server told us something meaningful" with "the network died." By encoding domain errors in `ValkeyResponse.Err`, users can pattern match, fold, or recover explicitly.

**Tradeoff:** More verbose call sites. Mitigated by:
- `.direct` extension method for users who prefer `F[A]` with exceptions
- `.liftTo[F]` for lifting into the effect error channel
- `fold`, `map`, `flatMap` on `ValkeyResponse` (Monad instance)

### 2. Codec[A] as simple encode/decode

**Decision:** `Codec[A]` is a plain typeclass with `encode: A => Array[Byte]` and `decode: Array[Byte] => A`.

**Why:** The Valkey wire protocol is byte-oriented. By operating on `Array[Byte]` rather than `GlideString` (Java Glide's wrapper), we:
- Decouple from any specific client library's types
- Enable binary-safe transport of arbitrary data
- Keep the codec interface minimal and implementable for any serialization format

**Rejected alternative:** Split `KeyCodec` / `ValueCodec`. Not worth the complexity -- a codec is just serialization, and most users use `String` for both keys and values.

### 3. Smart constructors with validated config

**Decision:** Config types (`ValkeyClientConfig`, `ValkeyClusterConfig`, `NodeAddress`, `DatabaseId`) use `apply` returning `Either[String, Config]` plus a `make[F]` helper for `ApplicativeThrow`.

**Why:** Invalid configurations (empty address lists, port out of range, database ID > 15) should fail at construction time, not when the first command is sent. `Either` for pure code, `make[F]` for effectful code.

**Concrete examples:**
- `NodeAddress` requires valid `com.comcast.ip4s.Host` and `Port` -- no raw strings
- `DatabaseId` is an opaque type validated to 0-15
- `ValkeyUri` parsing handles `redis://`, `rediss://`, `valkey://`, `valkeys://` schemes

### 4. ip4s for host/port types

**Decision:** Use `com.comcast.ip4s.{Host, Port}` instead of raw strings/ints.

**Why:** A `Host` is guaranteed to be a valid hostname or IP address. A `Port` is guaranteed to be in range 0-65535. This eliminates an entire category of "connected to wrong thing" bugs. The `host"..."` and `port"..."` literals make construction ergonomic.

### 5. Tagless final with concrete IO examples

**Decision:** All command traits are parameterized on `F[_]: Async`. The `Valkey[IO]` entry point is the primary user-facing API.

**Why:** Tagless final enables testing with alternative effect types and allows library consumers to use their own effect stack. But we don't force abstraction on users who just want `IO` -- the entry point is `Valkey[IO].utf8(uri)`.

---

## FFI Layer Decisions

### 6. Direct FFM over Java Glide client

**Decision:** Call `libglide_ffi.dylib` directly using JDK 22+ Foreign Function & Memory API, bypassing the Java Glide JNI wrapper entirely.

**Why:** The Java Glide client adds significant overhead:
1. Protobuf serialization of each command request
2. JNI boundary crossing (byte-array copy)
3. Protobuf deserialization of the response
4. `CompletableFuture` allocation per command
5. `GlideString` wrapping

With direct FFM, a command is: write args into an Arena → single downcall → upcall fires with response pointer → decode in-place. No serialization, no copies.

**Tradeoff:** We must parse the Rust `CommandResponse` struct layout ourselves. Any upstream struct changes break us. We mitigate this by pinning to a specific `valkey-glide` native library version.

### 7. Async callbacks via Deferred[F, *]

**Decision:** Each command registers a `Deferred[F, Either[NativeError, MemorySegment]]` keyed by a monotonically increasing callback ID. The native library fires an upcall stub that completes the Deferred.

**Why:** The Rust library is inherently async -- `command()` returns immediately (null) and delivers results via callback. We need to bridge this to Cats Effect's fiber model. `Deferred` is the natural choice:
- Exactly-once completion semantics
- Fiber suspends on `.get` without blocking a thread
- Type-safe success/failure via `Either`

**Alternative considered:** `CompletableFuture` (like Java Glide uses). Rejected because it adds an unnecessary Java synchronization primitive when we already have `Deferred` in scope.

### 8. Confined arenas for command arguments, shared arena for client lifecycle

**Decision:** Each command dispatch uses a short-lived `Arena.ofConfined()` for argument memory, while the client handle and upcall stubs live in a `Arena.ofShared()` tied to the `Resource` lifecycle.

**Why:**
- **Confined arenas** for args: arguments are only needed for the duration of the `command()` downcall. The native library copies arg data before returning. Confined arenas are cheaper (no thread-safety overhead) and deterministically freed.
- **Shared arena** for client: the client handle and upcall stubs must survive across threads and for the lifetime of the connection. `Resource.fromAutoCloseable` ensures cleanup on release.

**Contract assumption:** `GlideFfi.command` must not retain pointers to argument buffers after returning. This is guaranteed by the Glide FFI design (it copies args into Rust-owned memory for async dispatch).

### 9. CommandDispatcher as testable abstraction

**Decision:** The exec lifecycle (register → dispatch → await → decode → free) is encapsulated in a `CommandDispatcher[F]` trait. `BaseValkeyCommands` delegates to it.

**Why:** The exec lifecycle contains critical memory-safety logic (cancellation cleanup, free-after-decode ordering). Extracting it into a trait enables:
- Unit testing with a mock dispatcher (no native library needed)
- Verifying decode/free ordering in isolation
- Potential future alternative dispatchers (e.g., pipelined batch dispatch)

### 10. Cmd[A] bundles command type with decoder

**Decision:** `Cmd[A](ordinal: CmdOrdinal, decode: MemorySegment => A)` pairs a command's Rust enum ordinal with its type-safe decoder.

**Why:** The decoder must match the command's response type. By bundling them in a single `Cmd[A]`, the type parameter `A` ensures at compile time that `exec` returns the type that the decoder produces. You cannot accidentally pair a GET command (returns `Option[V]`) with a decoder that returns `Long`.

**What it doesn't do:** It doesn't prevent pairing the wrong ordinal with a decoder (e.g., using `CommandType.Get` with `Decode.long`). The command ordinal is a runtime value. Full compile-time safety would require 267 pre-defined singleton types -- the cost-benefit ratio doesn't justify it.

### 11. Decode object for reusable decoder functions

**Decision:** Fixed decoders (unit, long, boolean, string, double, etc.) are `val`s on the `Decode` object. Codec-dependent decoders are `def`s.

**Why:** Most commands share a small set of response shapes. By pre-allocating decoder functions as vals, we avoid lambda allocation per command call. The codec-dependent decoders (`optional[V]`, `list[V]`, `map[K,V]`) must be defs because they close over the specific codec instance.

---

## Error & Safety Decisions

### 12. Two-layer error model

**Decision:** Domain errors in `ValkeyResponse.Err`, infrastructure errors in `F`'s error channel.

| Layer | Captured in | Examples |
|-------|-------------|----------|
| Domain errors | `ValkeyResponse.Err` | WRONGTYPE, OOM, READONLY, CROSSSLOT |
| Infrastructure errors | `F` (MonadThrow) | Connection timeout, network partition, segfault |

**Why:** Domain errors are expected, recoverable conditions. Infrastructure errors are exceptional. Conflating them (as most Redis clients do) forces users to catch exceptions and inspect messages to distinguish "key has wrong type" from "server is down."

### 13. Credential redaction

**Decision:** `ServerCredentials.toString` returns `"Password(***)"`. Custom `equals`/`hashCode` prevent timing attacks on password comparison.

**Why:** Passwords appearing in logs is a common vulnerability in database clients. By overriding `toString` at the type level, accidental logging is impossible regardless of how the object is used.

**Remaining gap:** `ValkeyUri.toURI` embeds credentials in the URI. This is necessary for URI-based connection APIs but means `URI.toString()` leaks passwords. Users should never log the `URI` directly.

### 14. Response pointer lifecycle

**Decision:** The native response pointer is freed via `guarantee` (runs after decode completes, whether decode succeeds or fails). On fiber cancellation, the cleanup handler drains the `Deferred` and frees any response that arrived.

**Why:** The native library allocates a `CommandResponse` struct per response. If we don't call `free_command_response`, it leaks. The lifecycle must handle three cases:
1. **Normal:** decode → free (via `guarantee`)
2. **Decode throws:** free still runs (via `guarantee`)
3. **Fiber canceled:** response may have arrived → `tryGet` + free (via `onCancel`)

Without case 3, long-running applications under cancellation pressure would leak native memory.

### 15. Read-before-free in connection error path

**Decision:** When establishing a connection, the error message pointer is read and copied to a JVM string *before* calling `free_connection_response`.

**Why:** The error string may be allocated as part of the `ConnectionResponse` struct. If we free the struct first and then read the error pointer, we're dereferencing freed memory. The JVM won't segfault (it's just bytes at that point) but the message could be garbage. Reading first guarantees a valid error message.

---

## Type Design Decisions

### 16. ScoredValue[V] over (V, Double) tuples

**Decision:** Sorted set commands return `ScoredValue[V](value: V, score: Double)` instead of raw tuples.

**Why:** Tuples lose semantic meaning at the call site -- is it `(value, score)` or `(score, value)`? A named case class makes the API self-documenting. It also enables adding fields in the future without breaking call sites (e.g., adding a `rank` field).

### 17. ADTs over boolean parameters

**Decision:** Use sealed traits instead of boolean flags. Example: `InsertPosition.Before | After` instead of `insertBefore: Boolean`.

**Why:** Boolean blindness -- at the call site, `linsert(key, true, pivot, value)` tells you nothing. `linsert(key, InsertPosition.Before, pivot, value)` is self-documenting. This extends to `SetCondition.OnlyIfExists | OnlyIfNotExists`, `ExpireCondition`, etc.

### 18. ValkeyConnection ADT replacing Either

**Decision:** `ValkeyConnection` is `Standalone(client) | Clustered(client)` instead of `Either[StandaloneClient, ClusterClient]`.

**Why:** Server commands must dispatch differently based on connection mode (standalone uses `GlideClient`, cluster uses `GlideClusterClient`). An ADT with pattern matching is clearer than `either.fold(...)` or `either.left.map(...)`.

---

## What We Chose Not To Do

### Path-dependent types for command safety

**Considered:** Making each command a type member of a phantom-typed registry so that the return type is enforced at the type level with no runtime representation.

**Rejected:** 267 commands would require 267 type members. The `Cmd[A]` pattern already provides the compile-time guarantee that matters (return type consistency) without the boilerplate. The marginal safety gain (preventing wrong ordinal) doesn't justify the complexity.

### ResponseParser as a typeclass

**Considered:** `Decoder[A]` typeclass with `given` instances summoned by return type.

**Rejected:** Many decoders need a `Codec[V]` at the call site (e.g., `decodeOptional[V]`). Typeclasses work best when instances are fixed at definition site, not parameterized by call-site context. The `Decode` object with codec-parameterized defs is the right level of abstraction.

### Separate native module

**Considered:** A separate `modules/native/` sbt project for the FFM backend, keeping `modules/effects/` as the Java Glide backend.

**Rejected:** There is no Java Glide backend anymore -- we replaced it entirely. Keeping a dead module around adds confusion. The `effects` module now *is* the FFM backend.

### fs2 streams in the core module

**Considered:** Building stream consumption directly into `StreamCommands`.

**Rejected:** Not all users want an fs2 dependency. Continuous consumption is a higher-level concern (backpressure, ack strategies, crash recovery) that belongs in a dedicated opt-in module. The raw `xread`/`xreadgroup` commands remain in `effects` for users who prefer manual control.
