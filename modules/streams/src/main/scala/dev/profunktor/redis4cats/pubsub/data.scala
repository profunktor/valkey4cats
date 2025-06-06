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

package dev.profunktor.redis4cats.pubsub

import dev.profunktor.redis4cats.data.RedisChannel
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands

object data {

  trait RedisPubSubCommands[K, V] {
    def underlying: RedisPubSubAsyncCommands[K, V]
  }
  case class LivePubSubCommands[K, V](underlying: RedisPubSubAsyncCommands[K, V]) extends RedisPubSubCommands[K, V]

  case class Subscription[K](channel: RedisChannel[K], number: Long)

  object Subscription {
    def empty[K](channel: RedisChannel[K]): Subscription[K] =
      Subscription[K](channel, 0L)
  }

}
