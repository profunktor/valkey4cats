package dev.profunktor.valkey4cats.pubsub.internals

import cats.effect.Async
import cats.syntax.all.*
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.effect.{FutureLift, Log}
import dev.profunktor.valkey4cats.pubsub.{ValkeyChannel, ValkeyPattern, ValkeyPatternEvent}
import fs2.Stream
import glide.api.BaseClient
import glide.api.models.PubSubMessage

import scala.jdk.OptionConverters.*

private[pubsub] object MessageDispatcher {

  def pollLoop[F[_]: Async: Log, K: Codec, V: Codec](
      client: BaseClient,
      state: PubSubState[F, K, V]
  ): Stream[F, Unit] =
    Stream
      .repeatEval(FutureLift[F].lift(client.getPubSubMessage()))
      .evalMap(msg => dispatch(msg, state))
      .handleErrorWith { err =>
        Stream.eval(Log[F].error(s"PubSub dispatch error: ${err.getMessage}")) >>
          pollLoop(client, state)
      }

  private def dispatch[F[_]: Async, K: Codec, V: Codec](
      msg: PubSubMessage,
      state: PubSubState[F, K, V]
  ): F[Unit] = {
    val kCodec = Codec[K]
    val vCodec = Codec[V]
    val channel = kCodec.decode(msg.getChannel)
    val message = vCodec.decode(msg.getMessage)
    val patternOpt = msg.getPattern.toScala.map(kCodec.decode)

    patternOpt match {
      case Some(pattern) =>
        val key = ValkeyPattern(pattern)
        val event = ValkeyPatternEvent(pattern, channel, message)
        state.patternSubs.get.flatMap { subs =>
          subs.get(key).traverse_(_.topic.publish1(Some(event)))
        }
      case None =>
        val key = ValkeyChannel(channel)
        state.channelSubs.get.flatMap { subs =>
          subs.get(key).traverse_(_.topic.publish1(Some(message)))
        }
    }
  }
}
