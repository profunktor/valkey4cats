/*
 * Copyright 2018-2021 ProfunKtor
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
package streams

import cats.effect.kernel._
import dev.profunktor.redis4cats.StreamsInstances._
import dev.profunktor.redis4cats.connection._
import dev.profunktor.redis4cats.data._
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.effects.{ MessageId, StreamMessage, XReadOffsets }
import dev.profunktor.redis4cats.streams.data._
import fs2.Stream
import io.lettuce.core.{ ReadFrom => JReadFrom }

import scala.concurrent.duration.Duration

object RedisStream {

  def apply[F[_]: Sync, K, V](redis: RedisCommands[F, K, V]): RedisStream[F, K, V] = new RedisStream(redis)

  def mkStreamingConnection[F[_]: Async: Log, K, V](
      client: RedisClient,
      codec: RedisCodec[K, V]
  ): Stream[F, Streaming[F, Stream[F, *], K, V]] =
    Stream.resource(mkStreamingConnectionResource(client, codec))

  def mkStreamingConnectionResource[F[_]: Async: Log, K, V](
      client: RedisClient,
      codec: RedisCodec[K, V]
  ): Resource[F, Streaming[F, Stream[F, *], K, V]] =
    Redis[F].fromClient(client, codec).map(apply[F, K, V])

  def mkMasterReplicaConnection[F[_]: Async: Log, K, V](
      codec: RedisCodec[K, V],
      uris: RedisURI*
  )(readFrom: Option[JReadFrom] = None): Stream[F, Streaming[F, Stream[F, *], K, V]] =
    Stream.resource(mkMasterReplicaConnectionResource(codec, uris: _*)(readFrom))

  def mkMasterReplicaConnectionResource[F[_]: Async: Log, K, V](
      codec: RedisCodec[K, V],
      uris: RedisURI*
  )(readFrom: Option[JReadFrom] = None): Resource[F, Streaming[F, Stream[F, *], K, V]] =
    RedisMasterReplica[F]
      .make(codec, uris: _*)(readFrom)
      .flatMap(Redis[F].masterReplica)
      .map(apply[F, K, V])

}

class RedisStream[F[_]: Sync, K, V](redis: RedisCommands[F, K, V]) extends Streaming[F, Stream[F, *], K, V] {

  private[streams] def nextOffset(key: K, msg: StreamMessage[K, V]): XReadOffsets[K] =
    XReadOffsets.Custom(key, msg.id.value)

  private[streams] def offsetsByKey(iter: Iterable[StreamMessage[K, V]]): Iterator[(K, XReadOffsets[K])] = {
    val map = collection.mutable.Map.empty[K, StreamMessage[K, V]]
    iter.iterator.foreach(msg => map += msg.key -> msg)
    map.iterator.map { case (key, msg) => key -> nextOffset(key, msg) }
  }

  override def append: Stream[F, XAddMessage[K, V]] => Stream[F, MessageId] =
    _.evalMap(append)

  override def append(msg: XAddMessage[K, V]): F[MessageId] =
    redis.xAdd(msg.key, msg.body, msg.args)

  override def read(
      keys: Set[K],
      chunkSize: Int,
      initialOffset: K => XReadOffsets[K],
      block: Option[Duration],
      count: Option[Long],
      restartOnTimeout: RestartOnTimeout
  ): Stream[F, StreamMessage[K, V]] = {
    val initial = keys.map(k => k -> initialOffset(k)).toMap
    Stream.eval(Ref.of[F, Map[K, XReadOffsets[K]]](initial)).flatMap { ref =>
      def withoutRestarts =
        (for {
          offsets <- Stream.eval(ref.get)
          list <- Stream.eval(redis.xRead(offsets.values.toSet, block, count))
          offsetUpdates = offsetsByKey(list)
          _ <- Stream.eval(ref.update(map => offsetUpdates.foldLeft(map) { case (acc, (k, v)) => acc.updated(k, v) }))
          result <- Stream.fromIterator[F](list.iterator, chunkSize)
        } yield result).repeat

      restartOnTimeout.wrap(withoutRestarts)
    }
  }

}
