package dev.profunktor.valkey4cats

import cats.effect.*
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.connection.{ValkeyClient, ValkeyClusterClient}
import dev.profunktor.valkey4cats.effect.MkValkey
import dev.profunktor.valkey4cats.model.{
  ValkeyClientConfig,
  ValkeyClusterConfig,
  ValkeyUri
}

/** Main entry point for Valkey4S
  *
  * Example usage:
  * {{{
  *   Valkey[IO].utf8("redis://localhost:6379").use { valkey =>
  *     for {
  *       _ <- valkey.set("mykey", "myvalue")
  *       value <- valkey.get("mykey")
  *     } yield value
  *   }
  * }}}
  */
object Valkey {

  /** Partially applied Valkey DSL
    *
    * Provides various convenience constructors for creating Valkey clients
    */
  class ValkeyPartiallyApplied[F[_]: MkValkey: Async] {

    /** Create a UTF-8 string-based connection from URI
      *
      * This is the simplest way to get started with Valkey4S.
      *
      * Examples:
      * - "redis://localhost:6379"
      * - "rediss://secure-server:6380"
      * - "redis://:mypassword@localhost:6379/2"
      *
      * @param uri The connection URI
      * @return Resource managing the Valkey connection
      */
    def utf8(uri: String): Resource[F, ValkeyCommands[F, String, String]] =
      for {
        client <- MkValkey[F].clientFrom(uri)
        tx <- MkValkey[F].txRunner
      } yield new ValkeyStandalone[F, String, String](
        client,
        Codec.utf8Codec,
        Codec.utf8Codec,
        tx
      )

    def utf8(
        valkeyUri: ValkeyUri
    ): Resource[F, ValkeyCommands[F, String, String]] =
      for {
        client <- MkValkey[F].clientFromUri(valkeyUri)
        tx <- MkValkey[F].txRunner
      } yield new ValkeyStandalone[F, String, String](
        client,
        Codec.utf8Codec,
        Codec.utf8Codec,
        tx
      )

    /** Create a connection with custom codecs from URI
      *
      * @param uri The connection URI
      * @param kCodec Implicit codec for keys
      * @param vCodec Implicit codec for values
      * @return Resource managing the Valkey connection
      */
    def simple[K, V](
        uri: String
    )(implicit
        kCodec: Codec[K],
        vCodec: Codec[V]
    ): Resource[F, ValkeyCommands[F, K, V]] =
      for {
        client <- MkValkey[F].clientFrom(uri)
        tx <- MkValkey[F].txRunner
      } yield new ValkeyStandalone[F, K, V](client, kCodec, vCodec, tx)

    /** Create a connection from configuration
      *
      * Provides full control over client settings.
      *
      * @param config The client configuration
      * @param kCodec Implicit codec for keys
      * @param vCodec Implicit codec for values
      * @return Resource managing the Valkey connection
      */
    def fromConfig[K, V](
        config: ValkeyClientConfig
    )(implicit
        kCodec: Codec[K],
        vCodec: Codec[V]
    ): Resource[F, ValkeyCommands[F, K, V]] =
      for {
        client <- MkValkey[F].clientFromConfig(config)
        tx <- MkValkey[F].txRunner
      } yield new ValkeyStandalone[F, K, V](client, kCodec, vCodec, tx)

    /** Create commands from an existing client
      *
      * Useful when you need to manage the client lifecycle separately.
      *
      * @param client An existing ValkeyClient
      * @param kCodec Implicit codec for keys
      * @param vCodec Implicit codec for values
      * @return Resource managing the command interface
      */
    def fromClient[K, V](
        client: ValkeyClient
    )(implicit
        kCodec: Codec[K],
        vCodec: Codec[V]
    ): Resource[F, ValkeyCommands[F, K, V]] =
      MkValkey[F].txRunner.map { tx =>
        new ValkeyStandalone[F, K, V](client, kCodec, vCodec, tx)
      }

    /** Create a connection to localhost with defaults
      *
      * Equivalent to utf8("redis://localhost:6379")
      *
      * @return Resource managing the Valkey connection
      */
    def localhost: Resource[F, ValkeyCommands[F, String, String]] =
      utf8("redis://localhost:6379")

    // ==================== Cluster Support ====================

    /** Create a UTF-8 cluster connection from seed URIs
      *
      * @param seedUris One or more seed node URIs
      * @return Resource managing the cluster connection
      */
    def clusterUtf8(
        seedUris: String*
    ): Resource[F, ValkeyCommands[F, String, String]] =
      for {
        client <- MkValkey[F].clusterClientFromUris(seedUris*)
        tx <- MkValkey[F].txRunner
      } yield new ValkeyCluster[F, String, String](
        client,
        Codec.utf8Codec,
        Codec.utf8Codec,
        tx
      )

    /** Create a cluster connection with custom codecs
      *
      * @param seedUris One or more seed node URIs
      * @param kCodec Implicit codec for keys
      * @param vCodec Implicit codec for values
      * @return Resource managing the cluster connection
      */
    def cluster[K, V](
        seedUris: String*
    )(implicit
        kCodec: Codec[K],
        vCodec: Codec[V]
    ): Resource[F, ValkeyCommands[F, K, V]] =
      for {
        client <- MkValkey[F].clusterClientFromUris(seedUris*)
        tx <- MkValkey[F].txRunner
      } yield new ValkeyCluster[F, K, V](client, kCodec, vCodec, tx)

    /** Create a cluster connection from configuration
      *
      * @param config The cluster configuration
      * @param kCodec Implicit codec for keys
      * @param vCodec Implicit codec for values
      * @return Resource managing the cluster connection
      */
    def fromClusterConfig[K, V](
        config: ValkeyClusterConfig
    )(implicit
        kCodec: Codec[K],
        vCodec: Codec[V]
    ): Resource[F, ValkeyCommands[F, K, V]] =
      for {
        client <- MkValkey[F].clusterClient(config)
        tx <- MkValkey[F].txRunner
      } yield new ValkeyCluster[F, K, V](client, kCodec, vCodec, tx)

    /** Create commands from an existing cluster client
      *
      * @param client An existing ValkeyClusterClient
      * @param kCodec Implicit codec for keys
      * @param vCodec Implicit codec for values
      * @return Resource managing the command interface
      */
    def fromClusterClient[K, V](
        client: ValkeyClusterClient
    )(implicit
        kCodec: Codec[K],
        vCodec: Codec[V]
    ): Resource[F, ValkeyCommands[F, K, V]] =
      MkValkey[F].txRunner.map { tx =>
        new ValkeyCluster[F, K, V](client, kCodec, vCodec, tx)
      }
  }

  /** Main entry point for the Valkey DSL
    *
    * Usage:
    * {{{
    *   Valkey[IO].utf8("redis://localhost:6379")
    * }}}
    */
  def apply[F[_]: MkValkey: Async]: ValkeyPartiallyApplied[F] =
    new ValkeyPartiallyApplied[F]
}
