package dev.profunktor.valkey4cats.connection

import cats.effect._
import cats.syntax.all._
import glide.api.GlideClusterClient
import dev.profunktor.valkey4cats.effect.{FutureLift, Log, MkValkey}
import dev.profunktor.valkey4cats.model.ValkeyClusterConfig

/** Wrapper around Glide's cluster client with functional resource management
  *
  * @param underlying The underlying GlideClusterClient instance
  */
sealed abstract class ValkeyClusterClient private (
    private[valkey4cats] val underlying: GlideClusterClient
)

object ValkeyClusterClient {

  /** Private implementation class */
  private final class ValkeyClusterClientImpl(
      override val underlying: GlideClusterClient
  ) extends ValkeyClusterClient(underlying)

  /** Create a ValkeyClusterClient from configuration with Resource management
    *
    * @param config The cluster configuration
    * @return Resource that manages the client lifecycle
    */
  def fromConfig[F[_]: MkValkey](
      config: ValkeyClusterConfig
  )(implicit F: Async[F]): Resource[F, ValkeyClusterClient] = {
    implicit val futLift: FutureLift[F] = MkValkey[F].futureLift
    implicit val logger: Log[F] = MkValkey[F].log

    val acquire: F[ValkeyClusterClient] = for {
      _ <- logger.info(
        s"Creating Valkey cluster client for seed nodes: ${config.addresses.map(a => s"${a.host}:${a.port}").mkString(", ")}"
      )
      glideConfig = config.toGlide
      client <- FutureLift[F].lift(GlideClusterClient.createClient(glideConfig))
      _ <- logger.info("Valkey cluster client created successfully")
    } yield new ValkeyClusterClientImpl(client)

    val release: ValkeyClusterClient => F[Unit] = client =>
      (for {
        _ <- logger.info("Closing Valkey cluster client")
        _ <- FutureLift[F].blocking(client.underlying.close())
        _ <- logger.info("Valkey cluster client closed")
      } yield ()).handleErrorWith { err =>
        logger.error(s"Error closing Valkey cluster client: ${err.getMessage}")
      }

    Resource.make(acquire)(release)
  }

  /** Create from list of URI strings
    *
    * @param uris List of seed node URIs
    * @return Resource managing the cluster client
    */
  def fromUris[F[_]: MkValkey](
      uris: String*
  )(implicit F: Async[F]): Resource[F, ValkeyClusterClient] =
    Resource
      .eval(ValkeyClusterConfig.fromUris[F](uris.toList))
      .flatMap(fromConfig[F])
}
