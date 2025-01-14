package dev.profunktor.valkey4cats.effect

import cats.effect.*
import dev.profunktor.valkey4cats.connection.{ValkeyClient, ValkeyClusterClient}
import dev.profunktor.valkey4cats.model.{
  ValkeyClientConfig,
  ValkeyClusterConfig,
  ValkeyUri
}
import dev.profunktor.valkey4cats.tx.TxRunner

import scala.annotation.implicitNotFound

/** MkValkey is a capability trait that abstracts over the creation of ValkeyClient, ValkeyClusterClient,
  * and related infrastructure.
  *
  * It serves the internal purpose to orchestrate creation of such instances while avoiding impure constraints
  * such as `Async` or `Sync`.
  *
  * Users only need a `MkValkey` constraint and `Async` to create a `Valkey` instance.
  */
@implicitNotFound(
  "MkValkey instance not found. You can summon one by having instances for cats.effect.Async and dev.profunktor.valkey4cats.effect.Log in scope"
)
sealed trait MkValkey[F[_]] {

  /** Create standalone client from URI */
  def clientFromUri(uri: => ValkeyUri): Resource[F, ValkeyClient]

  def clientFrom(uri: => String): Resource[F, ValkeyClient]

  /** Create standalone client from config */
  def clientFromConfig(config: ValkeyClientConfig): Resource[F, ValkeyClient]

  /** Create cluster client from config */
  def clusterClient(
      config: ValkeyClusterConfig
  ): Resource[F, ValkeyClusterClient]

  /** Create cluster client from URI strings */
  def clusterClientFromUris(
      uris: String*
  ): Resource[F, ValkeyClusterClient]

  /** Transaction runner (stub for Phase 1, full implementation in Phase 3) */
  private[valkey4cats] def txRunner: Resource[F, TxRunner[F]]

  /** Access to FutureLift */
  private[valkey4cats] def futureLift: FutureLift[F]

  /** Logging capability */
  private[valkey4cats] def log: Log[F]
}

object MkValkey {
  def apply[F[_]: MkValkey]: MkValkey[F] = implicitly

  implicit def forAsync[F[_]: Async: Log]: MkValkey[F] = new MkValkey[F] {
    private implicit val implicitThis: MkValkey[F] = this

    /** Create standalone client from URI */
    override def clientFromUri(
        uri: => ValkeyUri
    ): Resource[F, ValkeyClient] =
      ValkeyClient[F].fromUri(uri)

    override def clientFrom(uri: => String): Resource[F, ValkeyClient] =
      ValkeyClient[F].fromUri(uri)

    def clientFromConfig(
        config: ValkeyClientConfig
    ): Resource[F, ValkeyClient] =
      ValkeyClient.acquireAndRelease[F](config)

    def clusterClient(
        config: ValkeyClusterConfig
    ): Resource[F, ValkeyClusterClient] =
      ValkeyClusterClient.fromConfig[F](config)

    def clusterClientFromUris(
        uris: String*
    ): Resource[F, ValkeyClusterClient] =
      ValkeyClusterClient.fromUris[F](uris*)

    private[valkey4cats] def txRunner: Resource[F, TxRunner[F]] =
      TxExecutor.make[F].map(TxRunner.make[F])

    private[valkey4cats] def futureLift: FutureLift[F] = implicitly

    private[valkey4cats] def log: Log[F] = implicitly

  }
}
