package dev.profunktor.valkey4cats

import cats.effect.{Async, Resource}
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.{ValkeyClientConfig, ValkeyClusterConfig}

object Valkey:

  def apply[F[_]: Async](using log: Log[F]): ValkeyPartiallyApplied[F] = new ValkeyPartiallyApplied[F]

  final class ValkeyPartiallyApplied[F[_]: Async](using log: Log[F]):

    def utf8(uri: String): Resource[F, ValkeyCommands[F, String, String]] =
      standalone[String, String](uri)(using Codec.utf8Codec, Codec.utf8Codec)

    def standalone[K, V](uri: String)(using kCodec: Codec[K], vCodec: Codec[V]): Resource[F, ValkeyCommands[F, K, V]] =
      for
        config <- Resource.eval(Async[F].fromEither(ValkeyClientConfig.fromUriString(uri)))
        client <- NativeClient.standalone[F](ConfigSerializer.serializeStandalone(config))
      yield new NativeValkey[F, K, V](client, kCodec, vCodec)

    def cluster[K, V](uris: String*)(using kCodec: Codec[K], vCodec: Codec[V]): Resource[F, ValkeyCommands[F, K, V]] =
      for
        config <- Resource.eval(ValkeyClusterConfig.fromUris[F](uris.toList))
        client <- NativeClient.cluster[F](ConfigSerializer.serializeCluster(config))
      yield new NativeValkey[F, K, V](client, kCodec, vCodec)

    def clusterUtf8(uris: String*): Resource[F, ValkeyCommands[F, String, String]] =
      cluster[String, String](uris*)(using Codec.utf8Codec, Codec.utf8Codec)

    def fromConfig[K, V](config: ValkeyClientConfig)(using kCodec: Codec[K], vCodec: Codec[V]): Resource[F, ValkeyCommands[F, K, V]] =
      NativeClient.standalone[F](ConfigSerializer.serializeStandalone(config))
        .map(client => new NativeValkey[F, K, V](client, kCodec, vCodec))

    def fromClusterConfig[K, V](config: ValkeyClusterConfig)(using kCodec: Codec[K], vCodec: Codec[V]): Resource[F, ValkeyCommands[F, K, V]] =
      NativeClient.cluster[F](ConfigSerializer.serializeCluster(config))
        .map(client => new NativeValkey[F, K, V](client, kCodec, vCodec))
