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
