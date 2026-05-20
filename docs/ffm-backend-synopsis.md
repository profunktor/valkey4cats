# FFM Native Backend — Synopsis

## What We're Doing

Replacing the Java Valkey Glide client (which uses JNI) with direct JDK 22+ Foreign Function & Memory (FFM) calls to `libglide_ffi.dylib`. This means we parse the Rust `CommandResponse` struct (96 bytes, `#[repr(C)]`) ourselves from Java-side memory segments.

---

## What Broke & How It Was Fixed

| Category | Bug | Root Cause | Fix Layer |
|----------|-----|-----------|-----------|
| **Element size** | Arrays decoded garbage | Old code assumed 16-byte array elements | Encoding — changed all `reinterpret` calls to use 96-byte `RESPONSE_SIZE` |
| **Map layout** | All map commands (HGETALL, CONFIG GET, etc.) crashed or returned wrong data | Assumed `map_key`/`map_value` at offsets 64/72 of the *outer* response pointed to parallel arrays. Actually: `array_value` → array of *entry* structs, each entry's offsets 64/72 are pointers to individual key/value CommandResponse structs | Encoding — rewrote `decodeMap` to iterate entries correctly |
| **Sets type** | SMEMBERS, SUNION, SDIFF returned empty | Response type `Sets=7` stores data at `sets_value` (offset 80) / `sets_value_len` (offset 88), not `array_value` (offset 48) | Encoding — added Sets branch to `decodeList` and new `decodeSet` method |
| **Float response** | INCRBYFLOAT threw NumberFormatException | Response has `response_type=Float` with value at `float_value` (offset 16), but code called `decodeString` which reads empty `string_value` | Encoding — use `ResponseParser.decodeDouble` which checks response type first |
| **LCSLEN wrong result** | LCS LEN returned the string instead of length | `LcsLen` was mapped to `CommandType.Lcs` without appending `"LEN"` arg | Encoding (arg construction) — append `"LEN".getBytes` to args |
| **MGET nulls** | Mix of existing/missing keys returned empty strings for missing | `decodeList` decoded Null-type elements as empty byte arrays → empty strings | Encoding — added `decodeOptionalList[V]` returning `List[Option[V]]`, used with `.collect` |
| **CustomCommand dispatch** | HEXPIRE, HGETEX, HTTL, etc. sent wrong command | `CustomCommand` (ordinal 1) creates an empty Rust `Cmd` and uses **first arg** as the command name. Code wasn't prepending the command name. | Encoding (arg construction) — added `customExec("HEXPIRE", args)` that prepends command name |
| **ZRangeWithScores** | "unknown command" error | Was mapped to `CustomCommand` instead of native `CommandType.ZRange` with `WITHSCORES` arg | Encoding (command type selection) — use `CommandType.ZRange` + append WITHSCORES |
| **Score pairs (ZRANGEBYSCORE WITHSCORES)** | Wrong values from sorted sets | Map-type response (type=6) needs the entry-pointer parsing, not alternating-pair parsing | Encoding — added Map branch to `decodeScorePairs` |

---

## Pattern: All Fixes Are at the Encoding/Decoding Layer

Every single bug was either:
1. **Wrong struct offset / size** — reading the wrong field or wrong element stride
2. **Wrong response type dispatch** — not handling all variants of `ResponseType` enum (Null/Int/Float/Bool/String/Array/Map/Sets/Ok/Error)
3. **Wrong arg construction** — not prepending command name for CustomCommand, missing sub-command args

None of these required changes to the algebra (trait signatures) or the `ValkeyResponse` ADT.

---

## Could Anything Be Elevated to the Type System?

| Opportunity | Where | Benefit | Feasibility |
|-------------|-------|---------|-------------|
| **`NonEmptyList` for varargs** | `del(keys: K*)`, `unlink(keys: K*)`, `mGet(keys: Set[K])` | Prevents sending zero-arg commands (which Valkey rejects) | High — HSETEX empty map failure is exactly this: server returns "numfields should be greater than 0". A `NonEmptyMap` / `NonEmptyList` constraint would catch at compile time |
| **`NonEmptyMap` for MSET/HSETEX** | `mSet(Map[K,V])`, `hSetEx(...)` | Same — server rejects empty | High |
| **Newtype for cursor** | `scan(cursor: String)` | Cursor is always "0" initially or a server-returned opaque string — raw String is too loose | Medium — could be `ScanCursor` opaque type (already have `ClusterScanCursor` sealed trait) |
| **Phantom type for WITHSCORES** | `zrangeWithScores` returns `List[(V, Double)]` vs `zrange` returns `List[V]` | Already separate methods, but could unify with a type-level flag | Low value — current API is clear |
| **ResponseType → sealed trait** | Internal parser dispatch | Replace magic ints (0-9) with ADT | Medium — purely internal, no user-facing benefit, but makes parser code safer |
| **`NonEmptyList` for LMPOP/BLMPOP keys** | `lmpop(keys: List[K], ...)` | Server requires at least one key | High |

**Verdict:** The highest-value type-system improvement is replacing `List[K]` / `Map[K,V]` with `NonEmptyList[K]` / `NonEmptyMap[K,V]` on commands that require at least one argument. The HSETEX test failure (`"numfields should be greater than 0"`) is a textbook case — the test sends an empty map, the server rejects it, and a `NonEmptyMap` parameter would make that test un-writable (correctly).

---

## Current State (2025-05-19)

- **488 / 560 tests pass** (87%)
- **Remaining 72 failures:**
  - ~55 are `NotImplementedError` stubs (scan, sscan, zscan, geoPos, geoSearch, geoSearchStore, xrange, xrevrange, xread, xreadgroup, xclaim, xpending, xautoclaim, zmpop, bzmpop, clusterScan)
  - ~6 are LMPOP/BLMPOP returning `None` (likely a nested-array decoding issue specific to that command's response shape)
  - ~3 are Map-response decoding issues (ZRANDMEMBERWITHSCORES, HRANDFIELDWITHCOUNTWITHVALUES — same pattern as the Map fix but these return Map with count param)
  - ~2 are LPOS returning wrong value for "not found" (Optional long decode: `-1` vs `None`)
  - ~2 are INFO on cluster (assertion issue, not struct parsing)
  - ~1 is HSETEX empty map (server rejects — test expectation mismatch, should be `NonEmptyMap`)

## CommandResponse Struct Layout (arm64)

```
offset  0: response_type (i32, ResponseType enum)
offset  8: int_value (i64)
offset 16: float_value (f64)
offset 24: bool_value (bool/i8)
offset 32: string_value (*mut c_char)
offset 40: string_value_len (c_long)
offset 48: array_value (*mut CommandResponse)
offset 56: array_value_len (c_long)
offset 64: map_key (*mut CommandResponse)
offset 72: map_value (*mut CommandResponse)
offset 80: sets_value (*mut CommandResponse)
offset 88: sets_value_len (c_long)
total: 96 bytes
```

## ResponseType Enum

```
Null=0, Int=1, Float=2, Bool=3, String=4, Array=5, Map=6, Sets=7, Ok=8, Error=9
```

## Key Insight: Map Type Layout

The Map response stores entries in `array_value` / `array_value_len`. Each entry is a full 96-byte CommandResponse struct where:
- `map_key` (offset 64) is a **pointer** to a separate CommandResponse (the key)
- `map_value` (offset 72) is a **pointer** to a separate CommandResponse (the value)

This is NOT two parallel arrays — it's an array of entry structs with per-entry key/value pointers.

## Java Glide Comparison

The Java Glide client uses JNI with a native method `valueFromPointer(long pointer)` — all struct-to-Java conversion happens in Rust JNI code. They never parse the struct from the Java side. We are the first consumer to parse `CommandResponse` from outside Rust.
