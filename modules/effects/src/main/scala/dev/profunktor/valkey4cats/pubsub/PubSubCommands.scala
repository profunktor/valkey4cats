package dev.profunktor.valkey4cats.pubsub

import cats.effect.*
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.connection.ValkeyClient
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.pubsub.internals.{LiveValkeyPubSub, MessageDispatcher, PubSubState}
import fs2.Stream

trait PubSubStats[F[_], K] {
  def pubsubChannels: F[List[ValkeyChannel[K]]]
  def pubsubChannels(pattern: K): F[List[ValkeyChannel[K]]]
  def pubsubNumPat: F[Long]
  def pubsubNumSub(channels: ValkeyChannel[K]*): F[Map[ValkeyChannel[K], Long]]
}

trait PublishCommands[F[_], K, V] extends PubSubStats[F, K] {
  def publish(channel: ValkeyChannel[K], message: V): F[Unit]
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

object ValkeyPubSub {

  /** Create a ValkeyPubSub instance from a ValkeyClient with Resource management.
    *
    * Starts a background fiber that polls for incoming messages and dispatches
    * them to the appropriate channel/pattern subscribers.
    *
    * @param client The ValkeyClient connection to use for pub/sub
    * @return Resource managing the pub/sub lifecycle (including the poll fiber)
    */
  def make[F[_]: Async: Log, K: Codec, V: Codec](
      client: ValkeyClient
  ): Resource[F, ValkeyPubSub[F, K, V]] =
    for {
      state <- Resource.eval(PubSubState.make[F, K, V])
      live = new LiveValkeyPubSub[F, K, V](client.underlying, state)
      _ <- Resource.make(
        Async[F].start(MessageDispatcher.pollLoop(client.underlying, state).compile.drain)
      )(fiber => fiber.cancel)
    } yield live

  /** Convenience constructor for String-typed pub/sub using UTF-8 encoding. */
  def utf8[F[_]: Async: Log](
      client: ValkeyClient
  ): Resource[F, ValkeyPubSub[F, String, String]] =
    make[F, String, String](client)
}
