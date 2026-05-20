# valkey4cats-streams: fs2 Streaming Module

## Motivation

The current `StreamCommands[F, K, V]` algebra exposes Valkey Streams as one-shot request/response operations. Users wanting continuous consumption must manually:

- Track last-seen message IDs across iterations
- Implement polling loops with backoff
- Manage the ack/nack lifecycle for consumer groups
- Coordinate graceful shutdown (stop polling, drain in-flight, final ack)

An fs2-based module eliminates this ceremony by modeling Valkey Streams as `fs2.Stream` — composable, backpressured, and resource-safe.

## Module Structure

```
modules/streams/
  src/main/scala/dev/profunktor/valkey4cats/streams/
    ValkeyStreams.scala        -- main entry point / resource constructor
    StreamEntry.scala          -- domain model for stream entries
    AckStrategy.scala          -- ack lifecycle configuration
    StreamConsumerConfig.scala -- consumer group config
```

**Artifact:** `valkey4cats-streams`
**Dependencies:** `valkey4cats-effects`, `fs2-core`

## Core Types

```scala
package dev.profunktor.valkey4cats.streams

import fs2.Stream

/** A single entry read from a Valkey stream. */
final case class StreamEntry[K, V](
    streamKey: K,
    messageId: String,
    fields: List[(K, V)],
    ack: F[Unit] // no-op for auto-ack strategies
)

/** How acknowledgement is handled for consumer group reads. */
sealed trait AckStrategy
object AckStrategy {
  /** Acknowledge each message immediately after it is emitted downstream. */
  case object AutoAck extends AckStrategy

  /** The caller is responsible for calling `entry.ack` on each StreamEntry. */
  case object ManualAck extends AckStrategy

  /** Acknowledge in batches of `n` messages. */
  final case class BatchAck(n: Int) extends AckStrategy
}

/** Configuration for consumer group streaming. */
final case class StreamConsumerConfig(
    batchSize: Int = 100,
    block: FiniteDuration = 1.second,
    ackStrategy: AckStrategy = AckStrategy.AutoAck,
    noAck: Boolean = false // server-side noack flag
)
```

## API Surface

```scala
trait ValkeyStreams[F[_], K, V] {

  /** Continuous read from one or more streams (no consumer group).
    * Tracks IDs internally — each poll resumes from the last-seen ID.
    */
  def read(
      keys: Map[K, String],       // initial IDs ("0" for all history, "$" for new only)
      batchSize: Int = 100,
      block: FiniteDuration = 1.second
  ): Stream[F, StreamEntry[K, V]]

  /** Consumer group read with configurable ack strategy.
    * Uses ">" as the initial ID (new messages only).
    * Automatically handles XREADGROUP + XACK lifecycle.
    */
  def readGroup(
      key: K,
      group: K,
      consumer: K,
      config: StreamConsumerConfig = StreamConsumerConfig()
  ): Stream[F, StreamEntry[K, V]]

  /** Produce entries to a stream. Emits the assigned entry ID for each input. */
  def append(key: K): fs2.Pipe[F, Map[K, V], String]

  /** Read pending messages for a consumer (re-delivery after crash/restart).
    * Emits entries with ID "0" to retrieve pending, then switches to ">".
    */
  def readPending(
      key: K,
      group: K,
      consumer: K,
      config: StreamConsumerConfig = StreamConsumerConfig()
  ): Stream[F, StreamEntry[K, V]]
}
```

## Implementation Strategy

### `read` — Standalone Polling

```scala
Stream.unfoldEval(initialIds) { lastIds =>
  xread(lastIds, batchSize, block.toMillis).map {
    case None    => Some((Chunk.empty, lastIds)) // timeout, re-poll
    case Some(m) =>
      val entries = flatten(m)
      val updatedIds = updateLastSeen(lastIds, entries)
      Some((Chunk.from(entries), updatedIds))
  }
}.unchunks
```

The server's `BLOCK` parameter does the waiting (no busy-poll). When no messages arrive within the block duration, `xread` returns `None` and we simply re-issue.

### `readGroup` — Consumer Group with Ack

```scala
val raw: Stream[F, StreamEntry[K, V]] =
  Stream.repeatEval(
    xreadgroup(group, consumer, Map(key -> ">"), config.batchSize, config.block.toMillis, config.noAck)
  ).flatMap(batch => Stream.emits(flatten(batch)))

config.ackStrategy match {
  case AutoAck    => raw.evalTap(e => xack(key, group, e.messageId))
  case ManualAck  => raw.map(e => e.copy(ack = xack(key, group, e.messageId)))
  case BatchAck(n) => raw.groupWithin(n, 1.second).evalTap { chunk =>
    xack(key, group, chunk.toList.map(_.messageId): _*)
  }.unchunks
}
```

### `append` — Producer Pipe

```scala
def append(key: K): Pipe[F, Map[K, V], String] =
  _.evalMap(fields => xadd(key, fields).flatMap(_.liftTo[F]))
```

### `readPending` — Crash Recovery

On startup, a consumer should first re-read its pending messages (those delivered but not acked before a crash), then switch to `>` for new messages:

```scala
def readPending(...): Stream[F, StreamEntry[K, V]] =
  readGroup(key, group, consumer, config.copy(...)).takeWhile(batch is non-empty)
    ++ readGroup(key, group, consumer, config) // switch to ">"
```

## Resource Safety & Cancellation

- `Stream` is intrinsically cancellation-safe — `F.canceled` or scope close halts polling.
- `ManualAck`: unacked messages become pending again (Valkey semantics). No data loss.
- `BatchAck`: on cancellation, partial batch ack can be handled via `Stream.onFinalize` to ack the remainder.
- The underlying `ValkeyCommands` resource is managed by the caller; `ValkeyStreams` borrows it — no separate connection lifecycle.

## FFM Backend Considerations

The FFM native backend dispatches commands as async callbacks. `xread` with `block > 0` means the Valkey server holds the connection until data arrives or timeout — the callback fires on response. This is fine because:

1. The callback ID is registered, the fiber suspends on `Deferred.get`
2. When the server responds (or times out), the FFI callback completes the `Deferred`
3. No thread is blocked client-side

**Risk:** If `block` is very large (minutes), the callback slot stays allocated. Mitigation: cap default block at a reasonable value (1-5 seconds) and re-poll, which also provides cancellation check points.

## Open Questions

1. **Multi-key readGroup** — Valkey supports reading from multiple streams in one XREADGROUP. Should `readGroup` accept `Map[K, String]` like the raw API, or keep it single-key for simplicity? (Recommendation: single-key, users can `Stream.merge` multiple.)

2. **Backpressure propagation** — If downstream is slow, should we stop polling (natural fs2 pull semantics) or buffer a bounded amount? (Recommendation: rely on fs2's pull-based demand; no internal buffer.)

3. **Error handling** — Should transient errors (connection reset) cause the stream to terminate, or should we retry with backoff? (Recommendation: terminate by default; users compose with `Stream.retry` or `.handleErrorWith` for their retry policy.)

4. **StreamEntry field representation** — Currently `List[(K, V)]`. Should this become a `Map[K, V]`? (Consideration: Valkey preserves field insertion order, and duplicate field names are legal in XADD. `List[(K, V)]` is more faithful.)

## Rejected Alternatives

### Wrapping xread in `Stream.awakeEvery`

Client-side timer polling ignores that `xread BLOCK` already does server-side waiting. Double latency, wasted round-trips.

### Separate connection per stream consumer

Unnecessary complexity. Valkey multiplexes commands on a single connection. The async dispatch model already allows concurrent in-flight commands.

### Typeclass-based StreamCodec

The existing `Codec[K]` / `Codec[V]` is sufficient. Stream field names and values are just keys and values — no special encoding.

## Build Integration

```scala
// build.sbt
lazy val streams = project
  .in(file("modules/streams"))
  .dependsOn(effects)
  .settings(commonSettings)
  .settings(
    name := "valkey4cats-streams",
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % Fs2Version
    )
  )
```

Root: `.aggregate(core, effects, streams, log4Cats, examples, microsite)`

## Estimated Scope

~250-300 lines of library code, plus ~200 lines of integration tests. No new native FFI work — this module composes on top of existing `StreamCommands`.
