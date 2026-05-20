package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.model.ValkeyResponse

/** Publish/subscribe messaging commands */
trait PubSubCommands[F[_], K, V] {

  /** Publish a message to a channel.
    *
    * @param channel The channel to publish to
    * @param message The message to send
    */
  def publish(channel: K, message: V): F[ValkeyResponse[Unit]]

  /** List active channels (those with at least one subscriber).
    *
    * @return List of active channel names
    */
  def pubsubChannels: F[ValkeyResponse[List[K]]]

  /** List active channels matching a pattern.
    *
    * @param pattern Glob-style pattern to match channel names
    * @return List of matching active channel names
    */
  def pubsubChannels(pattern: K): F[ValkeyResponse[List[K]]]

  /** Get the number of unique patterns that are subscribed to.
    *
    * @return The number of patterns
    */
  def pubsubNumPat: F[ValkeyResponse[Long]]

  /** Get the number of subscribers for the specified channels.
    *
    * @param channels The channels to query
    * @return Map of channel name to subscriber count
    */
  def pubsubNumSub(channels: K*): F[ValkeyResponse[Map[K, Long]]]
}
