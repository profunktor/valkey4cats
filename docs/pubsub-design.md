# Pub/Sub — Design Spec

## Problem

Valkey4cats currently only exposes the "publishing side" of pub/sub (`PUBLISH`, `PUBSUB CHANNELS`, `PUBSUB NUMPAT`, `PUBSUB NUMSUB`). There is no way to subscribe to channels and receive messages as an fs2 `Stream`. This is a core Valkey feature that users expect from a functional client.

## Glide Java API

Glide 2.4.0 provides two message delivery modes:

1. **Queue mode** (no callback) — messages accumulate in an internal `ConcurrentLinkedDeque`. Retrieved via:
   - `tryGetPubSubMessage()` — non-blocking poll, returns `null` if empty
   - `getPubSubMessage()` — returns `CompletableFuture<PubSubMessage>` that completes when a message arrives

2. **Callback mode** — a `MessageCallback` (BiConsumer) is set at client creation; messages are pushed to it on Glide's internal thread.

### Subscribe/Unsubscribe Methods

```java
// Fire-and-forget (updates local state, subscribes in background)
CompletableFuture<Void> subscribeLazy(Set<String> channels)
CompletableFuture<Void> psubscribeLazy(Set<String> patterns)

// With confirmation timeout
CompletableFuture<Void> subscribe(Set<String> channels, int timeoutMs)
CompletableFuture<Void> psubscribe(Set<String> patterns, int timeoutMs)

// Unsubscribe variants
CompletableFuture<Void> unsubscribeLazy(Set<String> channels)
CompletableFuture<Void> unsubscribeLazy()  // all channels
CompletableFuture<Void> punsubscribeLazy(Set<String> patterns)
CompletableFuture<Void> punsubscribeLazy()  // all patterns
CompletableFuture<Void> unsubscribe(Set<String> channels, int timeoutMs)
CompletableFuture<Void> punsubscribe(Set<String> patterns, int timeoutMs)
```

### PubSubMessage

```java
class PubSubMessage {
  GlideString getMessage()
  GlideString getChannel()
  Optional<GlideString> getPattern()  // present when matched via PSUBSCRIBE
}
```

### Subscription Configuration (at client creation)

```java
StandaloneSubscriptionConfiguration.builder()
  .subscription(EXACT, gs("channel-name"))
  .subscription(PATTERN, gs("news.*"))
  .callback(messageCallback, context)  // optional
  .build()
// Passed to GlideClientConfiguration.builder().subscriptionConfiguration(...)
```

## Design Decisions

- **Queue mode** (Approach A): We do not set a `MessageCallback`. Instead, a background fiber polls `getPubSubMessage()` in a loop and dispatches to per-channel Topics. This avoids the need for a `Dispatcher` to bridge Glide's internal threads into `F`.
- **Separate client type**: `ValkeyPubSub[F, K, V]` with its own `Resource` lifecycle, following redis4cats' pattern.
- **fs2 Stream delivery**: `subscribe(channel)` returns `Stream[F, V]` that emits messages until canceled or unsubscribed.
- **ADT for pattern messages**: `ValkeyPatternEvent[K, V]` carries pattern + channel + message.
- **Shared subscriptions**: Multiple `subscribe` calls for the same channel share one underlying Glide subscription (reference counted). The actual Glide `unsubscribe` fires only when the last subscriber stream terminates.
- **Dynamic + static subscriptions**: Channels can be declared at creation time (via config) AND added/removed dynamically at runtime.

## Scala API

### Data Types

```scala
package dev.profunktor.valkey4cats.pubsub

opaque type ValkeyChannel[K] = K
object ValkeyChannel {
  def apply[K](value: K): ValkeyChannel[K] = value
  extension [K](ch: ValkeyChannel[K]) def underlying: K = ch
}

opaque type ValkeyPattern[K] = K
object ValkeyPattern {
  def apply[K](value: K): ValkeyPattern[K] = value
  extension [K](p: ValkeyPattern[K]) def underlying: K = p
}

final case class ValkeyPatternEvent[K, V](pattern: K, channel: K, message: V)
```

### Algebras

```scala
package dev.profunktor.valkey4cats.pubsub

import fs2.Stream

trait PubSubStats[F[_], K] {
  def pubsubChannels: F[List[ValkeyChannel[K]]]
  def pubsubChannels(pattern: K): F[List[ValkeyChannel[K]]]
  def pubsubNumPat: F[Long]
  def pubsubNumSub(channels: ValkeyChannel[K]*): F[Map[ValkeyChannel[K], Long]]
}

trait PublishCommands[F[_], K, V] extends PubSubStats[F, K] {
  def publish(channel: ValkeyChannel[K], message: V): F[Long]
}

trait SubscribeCommands[F[_], K, V] {
  def subscribe(channel: ValkeyChannel[K]): Stream[F, V]
  def unsubscribe(channel: ValkeyChannel[K]): F[Unit]
  def psubscribe(pattern: ValkeyPattern[K]): Stream[F, ValkeyPatternEvent[K, V]]
  def punsubscribe(pattern: ValkeyPattern[K]): F[Unit]
}

trait ValkeyPubSub[F[_], K, V]
    extends PublishCommands[F, K, V]
    with SubscribeCommands[F, K, V]
```

### Entry Point

```scala
package dev.profunktor.valkey4cats.pubsub

import cats.effect.*
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.connection.{ValkeyClient, ValkeyClusterClient}

object ValkeyPubSub {

  def make[F[_]: Async, K, V](
      client: ValkeyClient
  )(using kCodec: Codec[K], vCodec: Codec[V]): Resource[F, ValkeyPubSub[F, K, V]]

  def makeCluster[F[_]: Async, K, V](
      client: ValkeyClusterClient
  )(using kCodec: Codec[K], vCodec: Codec[V]): Resource[F, ValkeyPubSub[F, K, V]]

  def utf8(client: ValkeyClient): Resource[F, ValkeyPubSub[F, String, String]]
  def clusterUtf8(client: ValkeyClusterClient): Resource[F, ValkeyPubSub[F, String, String]]
}
```

### PubSubConfig (optional initial subscriptions)

```scala
package dev.profunktor.valkey4cats.pubsub

final case class PubSubConfig[K](
    channels: Set[ValkeyChannel[K]] = Set.empty,
    patterns: Set[ValkeyPattern[K]] = Set.empty
)
```

When provided to the constructor, these subscriptions are established at client creation time (wired into `StandaloneSubscriptionConfiguration`/`ClusterSubscriptionConfiguration`).

## Internal Architecture

### Message Dispatch Loop

```
┌─────────────────────────────────────┐
│  Glide BaseClient (queue mode)      │
│  Internal ConcurrentLinkedDeque      │
└─────────────┬───────────────────────┘
              │ getPubSubMessage()
              ▼
┌─────────────────────────────────────┐
│  Background Fiber (poll loop)        │
│  Stream.repeatEval(liftFuture(...)) │
└─────────────┬───────────────────────┘
              │ decode + dispatch
              ▼
┌─────────────────────────────────────┐
│  Per-channel Topic[F, Option[V]]    │
│  or per-pattern Topic[F, Option[E]] │
└─────────────┬───────────────────────┘
              │ topic.subscribe
              ▼
┌─────────────────────────────────────┐
│  User's Stream[F, V]                │
└─────────────────────────────────────┘
```

1. A single background fiber calls `getPubSubMessage()` in a loop via `FutureLift`.
2. Each received `PubSubMessage` is decoded (channel/pattern bytes → `K`, message bytes → `V`).
3. Based on the channel/pattern, the message is published to the corresponding `Topic[F, Option[V]]`.
4. Each user-facing `Stream` is derived from `topic.subscribe.unNoneTerminate`.
5. Publishing `None` to a topic terminates all subscriber streams for that channel.

### Subscription State

```scala
private case class SubState[F[_], V](
    topic: Topic[F, Option[V]],
    subscribers: Int,
    cleanup: F[Unit]
)
```

Managed via `AtomicCell[F, Map[ValkeyChannel[K], SubState[F, V]]]` (and similarly for patterns).

- `subscribe(channel)`: If entry exists, increment subscriber count and return `topic.subscribe`. If not, call Glide's `subscribeLazy`, create topic, store state.
- Stream finalization: Decrement count. If last subscriber, run `cleanup` (Glide `unsubscribeLazy` + remove topic).
- `unsubscribe(channel)`: Publish `None` to topic (terminates all streams), which triggers cleanup via finalization.

### Resource Lifecycle

The `Resource` manages:
1. The background poll fiber (canceled on release)
2. All active subscriptions (unsubscribed on release)
3. Does NOT own the underlying `ValkeyClient` — the caller manages that lifecycle

## Usage Examples

```scala
import dev.profunktor.valkey4cats.pubsub.*
import dev.profunktor.valkey4cats.connection.ValkeyClient

// Basic subscribe + publish
ValkeyClient[IO].from("redis://localhost").flatMap { client =>
  ValkeyPubSub.utf8(client)
}.use { pubsub =>
  val messages: Stream[IO, String] =
    pubsub.subscribe(ValkeyChannel("notifications"))

  val publisher: IO[Long] =
    pubsub.publish(ValkeyChannel("notifications"), "hello!")

  messages
    .take(1)
    .concurrently(Stream.eval(IO.sleep(100.millis) >> publisher))
    .compile
    .lastOrError
}

// Pattern subscription
ValkeyPubSub.utf8(client).use { pubsub =>
  val events: Stream[IO, ValkeyPatternEvent[String, String]] =
    pubsub.psubscribe(ValkeyPattern("news.*"))

  events.evalMap(evt => IO.println(s"${evt.pattern} → ${evt.channel}: ${evt.message}"))
}
```

## File Changes

| File | Change |
|------|--------|
| `modules/effects/src/main/scala/dev/profunktor/valkey4cats/pubsub/data.scala` | New — `ValkeyChannel`, `ValkeyPattern`, `ValkeyPatternEvent` |
| `modules/effects/src/main/scala/dev/profunktor/valkey4cats/pubsub/ValkeyPubSub.scala` | New — algebra traits + companion entry point |
| `modules/effects/src/main/scala/dev/profunktor/valkey4cats/pubsub/PubSubConfig.scala` | New — optional initial subscription config |
| `modules/effects/src/main/scala/dev/profunktor/valkey4cats/pubsub/internals/PubSubState.scala` | New — AtomicCell-based subscription state |
| `modules/effects/src/main/scala/dev/profunktor/valkey4cats/pubsub/internals/LiveValkeyPubSub.scala` | New — implementation |
| `modules/effects/src/main/scala/dev/profunktor/valkey4cats/pubsub/internals/MessageDispatcher.scala` | New — background poll fiber + topic dispatch |
| `modules/effects/src/test/scala/dev/profunktor/valkey4cats/pubsub/ValkeyPubSubSuite.scala` | New — integration tests |

## Existing PubSubCommands on ValkeyCommands

The existing `PubSubCommands[F, K, V]` trait on `ValkeyCommands` (`publish`, `pubsubChannels`, `pubsubNumPat`, `pubsubNumSub`) remains unchanged. It provides fire-and-forget publishing and introspection without needing a dedicated pub/sub connection. The new `ValkeyPubSub` is for when you need to *receive* messages.

## Testing

1. **Integration test**: Create pub/sub client, subscribe to channel, publish from regular client, assert message received via stream.
2. **Pattern test**: Subscribe to `"test.*"`, publish to `"test.hello"`, verify `ValkeyPatternEvent` fields.
3. **Multi-subscriber test**: Two streams on same channel, publish once, both receive.
4. **Unsubscribe test**: Subscribe, unsubscribe, verify stream terminates.
5. **Cleanup test**: Cancel stream, verify Glide unsubscribe is called (subscription count drops).
