/*
 * Copyright 2018-2025 ProfunKtor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.profunktor.redis4cats
package pubsub

import dev.profunktor.redis4cats.data._
import dev.profunktor.redis4cats.pubsub.data.Subscription

trait PubSubStats[F[_], K] {
  def numPat: F[Long]
  def numSub: F[List[Subscription[K]]]
  def pubSubChannels: F[List[RedisChannel[K]]]
  def pubSubShardChannels: F[List[RedisChannel[K]]]
  def pubSubSubscriptions(channel: RedisChannel[K]): F[Option[Subscription[K]]]
  def pubSubSubscriptions(channels: List[RedisChannel[K]]): F[List[Subscription[K]]]
  def shardNumSub(channels: List[RedisChannel[K]]): F[List[Subscription[K]]]
}

/** @tparam F
  *   the effect type
  * @tparam S
  *   the stream type
  * @tparam K
  *   the channel key type
  * @tparam V
  *   the value type
  */
trait PublishCommands[F[_], S[_], K, V] extends PubSubStats[F, K] {

  /** @return The number of clients that received the message. */
  def publish(channel: RedisChannel[K]): S[V] => S[Long]

  /** @return The number of clients that received the message. */
  def publish(channel: RedisChannel[K], value: V): F[Long]
}

/** @tparam F
  *   the effect type
  * @tparam S
  *   the stream type
  * @tparam K
  *   the channel key type
  * @tparam V
  *   the value type
  */
trait SubscribeCommands[F[_], S[_], K, V] {

  /** Subscribes to a channel.
    *
    * @note
    *   If you invoke `subscribe` multiple times for the same channel, we will not call 'SUBSCRIBE' in Redis multiple
    *   times but instead will return a stream that will use the existing subscription to that channel. The underlying
    *   subscription is cleaned up when all the streams terminate or when `unsubscribe` is invoked.
    */
  def subscribe(channel: RedisChannel[K]): S[V]

  /** Terminates all streams that are subscribed to the channel. */
  def unsubscribe(channel: RedisChannel[K]): F[Unit]

  /** Subscribes to a pattern.
    *
    * @note
    *   If you invoke `subscribe` multiple times for the same pattern, we will not call 'SUBSCRIBE' in Redis multiple
    *   times but instead will return a stream that will use the existing subscription to that pattern. The underlying
    *   subscription is cleaned up when all the streams terminate or when `unsubscribe` is invoked.
    */
  def psubscribe(channel: RedisPattern[K]): S[RedisPatternEvent[K, V]]

  /** Terminates all streams that are subscribed to the pattern. */
  def punsubscribe(channel: RedisPattern[K]): F[Unit]

  /** Returns the channel subscriptions that the library keeps of.
    *
    * @return
    *   how many streams are subscribed to each channel.
    * @see
    *   [[SubscribeCommands.subscribe]] for more information.
    */
  def internalChannelSubscriptions: F[Map[RedisChannel[K], Long]]

  /** Returns the pattern subscriptions that the library keeps of.
    *
    * @return
    *   how many streams are subscribed to each pattern.
    * @see
    *   [[SubscribeCommands.psubscribe]] for more information.
    */
  def internalPatternSubscriptions: F[Map[RedisPattern[K], Long]]
}

/** @tparam F
  *   the effect type
  * @tparam S
  *   the stream type
  * @tparam K
  *   the channel key type
  * @tparam V
  *   the value type
  */
trait PubSubCommands[F[_], S[_], K, V] extends PublishCommands[F, S, K, V] with SubscribeCommands[F, S, K, V]
