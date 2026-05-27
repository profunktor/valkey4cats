package dev.profunktor.valkey4cats.pubsub.internals

import cats.Applicative
import cats.effect.Async
import cats.effect.std.AtomicCell
import cats.syntax.all.*
import dev.profunktor.valkey4cats.pubsub.{ValkeyChannel, ValkeyPattern, ValkeyPatternEvent}
import fs2.Stream
import fs2.concurrent.Topic

private[pubsub] final case class SubState[F[_], V](
    topic: Topic[F, Option[V]],
    subscribers: Int,
    cleanup: F[Unit]
) {
  def addSubscriber: SubState[F, V] = copy(subscribers = subscribers + 1)
  def removeSubscriber: SubState[F, V] = copy(subscribers = subscribers - 1)
  def isLastSubscriber: Boolean = subscribers <= 1

  def stream(onFinalize: F[Unit])(implicit F: Applicative[F]): Stream[F, V] =
    topic.subscribe(128).unNoneTerminate.onFinalize(onFinalize)
}

private[pubsub] final case class PubSubState[F[_], K, V](
    channelSubs: AtomicCell[F, Map[ValkeyChannel[K], SubState[F, V]]],
    patternSubs: AtomicCell[F, Map[ValkeyPattern[K], SubState[F, ValkeyPatternEvent[K, V]]]]
)

private[pubsub] object PubSubState {
  def make[F[_]: Async, K, V]: F[PubSubState[F, K, V]] =
    for {
      cs <- AtomicCell[F].of(Map.empty[ValkeyChannel[K], SubState[F, V]])
      ps <- AtomicCell[F].of(Map.empty[ValkeyPattern[K], SubState[F, ValkeyPatternEvent[K, V]]])
    } yield PubSubState(cs, ps)
}
