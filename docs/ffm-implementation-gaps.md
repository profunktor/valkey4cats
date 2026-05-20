# FFM Implementation Gaps

Tracking known gaps between the previous Java Glide implementation and the current FFM native layer.

---

## Threading & Concurrency

### 1. Transaction Thread Pinning
**Status:** Not wired  
**What Java did:** `TxExecutor` allocated a single-thread `ExecutorService` to ensure all commands within a MULTI/EXEC block dispatched on the same thread, preventing interleaving.  
**Current state:** `TxExecutor` and `TxThreadFactory` exist in core but aren't connected to the FFM dispatch path.  
**Fix:** Wire `TxRunner` into the command execution path. For FFM, transactions likely need a dedicated callback ID range or a separate `command_with_route` call to ensure slot pinning.

### 2. Arena Strategy for Command Arguments
**Status:** Using shared arenas (correct but suboptimal)  
**What's ideal:** `Arena.ofConfined()` per command (no thread-safety atomics) since args are allocated, passed to Rust (which copies them), then freed — all synchronously.  
**Blocker:** CE fibers can resume on different threads. If alloc and free happen in different `IO.delay` blocks, confined arenas segfault.  
**Fix:** Ensure alloc + FFM downcall + arena close happen in a single `IO { ... }` block (no suspension point between them). This is safe because the downcall is non-blocking — Rust copies args immediately and returns.

---

## Features Not Yet Implemented

### 3. Pub/Sub Callback Registration
**Status:** Not implemented  
**What Java did:** Dedicated pub/sub listener threads with message dispatch to subscriber callbacks.  
**What FFM needs:** The Go FFI crate exposes `register_pubsub_callback` / `unregister_pubsub_callback`. Need a second upcall stub specifically for pub/sub message delivery, routed to an `fs2.Stream` or `Queue`.

### 4. Cluster SCAN Cursor Lifecycle
**Status:** Cursor is now opaque (`ClusterScanCursor` backed by a boolean)  
**What Java did:** Wrapped `glide.api.models.commands.scan.ClusterScanCursor` which tracked slot iteration state internally.  
**What FFM needs:** The native `scan` response includes cursor state as bytes. Parse the response to extract cursor position and finished flag.

### 5. Script Caching (`store_script` / `drop_script`)
**Status:** Not implemented  
**What's available in FFI crate:** `store_script(script: *const u8, len: usize) -> hash`, `drop_script(hash)`.

### 6. OpenTelemetry Span Management
**Status:** Not implemented  
**What's available in FFI crate:** Span creation/attachment functions for distributed tracing.

### 7. Logging Integration
**Status:** Not implemented  
**What's available in FFI crate:** `init(level, file_name)`, `glide_log(level, msg, len)` — native-side logging that could integrate with Log[F].

---

## Config Serialization

### 8. Connection Config Protobuf
**Status:** Not implemented  
**Context:** `create_client` accepts protobuf-encoded connection config (the *only* place protobuf is used in the FFI path — command dispatch is raw args). Need a protobuf serializer for `ConnectionRequest` or use the simpler alternative: the Go client's approach of passing config fields as individual args.  
**Decision needed:** Use scalapb to generate `ConnectionRequest` serialization, or contribute a non-protobuf `create_client` variant upstream.

---

## Response Parsing

### 9. Full RESP3 Type Coverage
**Status:** Partial (Long, Boolean, String, Optional, List, Map)  
**Missing:** Double responses, Set responses, nested Map/Array, Error type discrimination from response metadata, Null vs empty distinction in arrays.

### 10. Memory Lifecycle for Responses
**Status:** Design exists (`free_command_response`) but not wired into the execution path  
**Risk:** Memory leak if a fiber is cancelled between receiving a response pointer and calling free.  
**Fix:** Use `Resource.make(decode)(free)` or `IO.guarantee` around response handling.

---

## Resolved

- [x] Callback throughput — switched from `Dispatcher.sequential` to `Dispatcher.parallel` (supports concurrent upcall processing from multiple Rust worker threads)
- [x] Arena strategy — switched per-command arena from `ofShared()` to `ofConfined()`. Safe because alloc → downcall → close is a single `IO { }` block with no suspension points. Rust copies args synchronously.
