package dev.profunktor.valkey4cats.pubsub.internals

import cats.effect.Async
import cats.syntax.all.*
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.effect.FutureLift
import dev.profunktor.valkey4cats.pubsub.*
import fs2.Stream
import fs2.concurrent.Topic
import glide.api.BaseClient
import glide.api.models.GlideString

import scala.jdk.CollectionConverters.*

private[pubsub] final class LiveValkeyPubSub[F[_]: Async, K: Codec, V: Codec](
    client: BaseClient,
    state: PubSubState[F, K, V]
) extends ValkeyPubSub[F, K, V] {

  private val kCodec = Codec[K]
  private val vCodec = Codec[V]

  // Helper: convert K to String for Glide subscribe/unsubscribe APIs
  private def keyToString(k: K): String =
    kCodec.encode(k).getString

  // --- SubscribeCommands ---

  override def subscribe(channel: ValkeyChannel[K]): Stream[F, V] =
    Stream.eval(acquireChannelSub(channel)).flatten

  override def unsubscribe(channel: ValkeyChannel[K]): F[Unit] =
    state.channelSubs.evalUpdate { subs =>
      subs.get(channel) match {
        case None      => subs.pure
        case Some(sub) => sub.topic.publish1(None).as(subs)
      }
    }

  override def psubscribe(pattern: ValkeyPattern[K]): Stream[F, ValkeyPatternEvent[K, V]] =
    Stream.eval(acquirePatternSub(pattern)).flatten

  override def punsubscribe(pattern: ValkeyPattern[K]): F[Unit] =
    state.patternSubs.evalUpdate { subs =>
      subs.get(pattern) match {
        case None      => subs.pure
        case Some(sub) => sub.topic.publish1(None).as(subs)
      }
    }

  // --- PublishCommands ---

  override def publish(channel: ValkeyChannel[K], message: V): F[Unit] = {
    val ch = kCodec.encode(channel.underlying)
    val msg = vCodec.encode(message)
    // Glide publish takes (message, channel) argument order
    FutureLift[F].lift(client.publish(msg, ch)).void
  }

  // --- PubSubStats ---

  override def pubsubChannels: F[List[ValkeyChannel[K]]] =
    FutureLift[F].lift(client.pubsubChannels()).map { arr =>
      arr.toList.map(s => ValkeyChannel(kCodec.decode(GlideString.of(s))))
    }

  override def pubsubChannels(pattern: K): F[List[ValkeyChannel[K]]] =
    FutureLift[F].lift(client.pubsubChannels(keyToString(pattern))).map { arr =>
      arr.toList.map(s => ValkeyChannel(kCodec.decode(GlideString.of(s))))
    }

  override def pubsubNumPat: F[Long] =
    FutureLift[F].lift(client.pubsubNumPat()).map(_.toLong)

  override def pubsubNumSub(channels: ValkeyChannel[K]*): F[Map[ValkeyChannel[K], Long]] =
    FutureLift[F]
      .lift(client.pubsubNumSub(channels.map(ch => keyToString(ch.underlying)).toArray))
      .map { jmap =>
        jmap.asScala.map { case (k, v) =>
          ValkeyChannel(kCodec.decode(GlideString.of(k))) -> v.toLong
        }.toMap
      }

  // --- Internal subscription management ---

  private def acquireChannelSub(channel: ValkeyChannel[K]): F[Stream[F, V]] =
    state.channelSubs.evalModify { subs =>
      subs.get(channel) match {
        case Some(sub) =>
          val updated = sub.addSubscriber
          (subs.updated(channel, updated), updated.stream(onChannelStreamFinalize(channel))).pure

        case None =>
          for {
            topic <- Topic[F, Option[V]]
            _ <- FutureLift[F].lift(
              client.subscribeLazy(java.util.Set.of(keyToString(channel.underlying)))
            )
            cleanup = FutureLift[F]
              .lift(client.unsubscribeLazy(java.util.Set.of(keyToString(channel.underlying))))
              .void
            sub = SubState(topic, subscribers = 1, cleanup)
          } yield (subs.updated(channel, sub), sub.stream(onChannelStreamFinalize(channel)))
      }
    }

  private def onChannelStreamFinalize(channel: ValkeyChannel[K]): F[Unit] =
    state.channelSubs.evalUpdate { subs =>
      subs.get(channel) match {
        case None => subs.pure
        case Some(sub) =>
          if (sub.isLastSubscriber) sub.cleanup.as(subs - channel)
          else (subs.updated(channel, sub.removeSubscriber)).pure
      }
    }

  private def acquirePatternSub(pattern: ValkeyPattern[K]): F[Stream[F, ValkeyPatternEvent[K, V]]] =
    state.patternSubs.evalModify { subs =>
      subs.get(pattern) match {
        case Some(sub) =>
          val updated = sub.addSubscriber
          (subs.updated(pattern, updated), updated.stream(onPatternStreamFinalize(pattern))).pure

        case None =>
          for {
            topic <- Topic[F, Option[ValkeyPatternEvent[K, V]]]
            _ <- FutureLift[F].lift(
              client.psubscribeLazy(java.util.Set.of(keyToString(pattern.underlying)))
            )
            cleanup = FutureLift[F]
              .lift(client.punsubscribeLazy(java.util.Set.of(keyToString(pattern.underlying))))
              .void
            sub = SubState(topic, subscribers = 1, cleanup)
          } yield (subs.updated(pattern, sub), sub.stream(onPatternStreamFinalize(pattern)))
      }
    }

  private def onPatternStreamFinalize(pattern: ValkeyPattern[K]): F[Unit] =
    state.patternSubs.evalUpdate { subs =>
      subs.get(pattern) match {
        case None => subs.pure
        case Some(sub) =>
          if (sub.isLastSubscriber) sub.cleanup.as(subs - pattern)
          else (subs.updated(pattern, sub.removeSubscriber)).pure
      }
    }
}
