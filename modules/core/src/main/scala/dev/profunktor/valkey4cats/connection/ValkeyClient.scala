package dev.profunktor.valkey4cats.connection

import cats.MonadThrow
import cats.effect.kernel.Resource
import cats.syntax.all.*
import glide.api.GlideClient
import dev.profunktor.valkey4cats.effect.{FutureLift, Log, MkValkey}
import dev.profunktor.valkey4cats.model.{ValkeyClientConfig, ValkeyUri}

/** Wrapper around Glide's standalone client with functional resource management
  *
  * @param underlying The underlying GlideClient instance
  */
sealed abstract class ValkeyClient private (
    private[valkey4cats] val underlying: GlideClient
)

object ValkeyClient {

  /** Private implementation class */
  private final class ValkeyClientImpl(
      override val underlying: GlideClient
  ) extends ValkeyClient(underlying)

  private[valkey4cats] def apply(underlying: GlideClient): ValkeyClient =
    new ValkeyClientImpl(underlying)

  /** Create a ValkeyClient from configuration with Resource management
   *
   * @param config The client configuration
   * @return Resource that manages the client lifecycle
   */
  def acquireAndRelease[F[_]: FutureLift: Log: MonadThrow](
      config: ValkeyClientConfig
  ): Resource[F, ValkeyClient] = {
    val glideConfig = config.toGlide
    val acquire: F[ValkeyClient] =
      Log[F].info(
        s"Creating Valkey client for addresses: ${config.addresses.map(a => s"${a.host}:${a.port}").mkString(", ")}"
      ) *> FutureLift[F]
        .lift(GlideClient.createClient(glideConfig))
        .map(client => ValkeyClient.apply(client)) <* Log[F].info(
        "Valkey client created successfully"
      )

    val release: ValkeyClient => F[Unit] = client =>
      {
        Log[F].info("Closing Valkey client") *> FutureLift[F].blocking(
          client.underlying.close()
        ) *> Log[F].info("Valkey client closed")
      }.handleErrorWith { err =>
        Log[F].error(s"Error closing Valkey client: ${err.getMessage}")
      }

    Resource.make(acquire)(release)
  }

  class ValkeyClientPartiallyApplied[F[_]: MkValkey: MonadThrow] {
    implicit val fl: FutureLift[F] = MkValkey[F].futureLift
    implicit val log: Log[F] = MkValkey[F].log

    def from(strUri: => String): Resource[F, ValkeyClient] =
      Resource.eval(ValkeyUri.make[F](strUri)).flatMap(this.fromUri)

    /** Convenience constructor from URI string
     *
     * Examples:
     * - "redis://localhost:6379"
     * - "rediss://my-secure-server:6380"
     * - "redis://:mypassword@localhost:6379/2"
     *
     * @param uri The connection URI
     * @return Resource managing the client
     */
    def fromUri(
        uri: ValkeyUri
    ): Resource[F, ValkeyClient] = {
      val config = ValkeyClientConfig.fromUri(uri)
      acquireAndRelease[F](config)
    }

    def fromUri(uri: String): Resource[F, ValkeyClient] = {
      Resource
        .eval(ValkeyClientConfig.fromUri(uri))
        .flatMap(acquireAndRelease[F])
    }

    /** Convenience constructor for localhost with defaults */
    def localhost: Resource[F, ValkeyClient] =
      acquireAndRelease(ValkeyClientConfig.localhost)

  }
  def apply[F[_]: MkValkey: MonadThrow]: ValkeyClientPartiallyApplied[F] =
    new ValkeyClientPartiallyApplied[F]

}
