package dev.profunktor.valkey4cats.model

import cats.effect.Sync
import glide.api.models.configuration as G

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** TLS encryption mode for Valkey connection
  */
sealed trait TlsMode { self =>

  /** Check if TLS is enabled */
  def isEnabled: Boolean = self match {
    case TlsMode.Disabled   => false
    case TlsMode.Enabled(_) => true
  }

  private[valkey4cats] def toGlide
      : (Boolean, Option[G.TlsAdvancedConfiguration]) =
    self match {
      case TlsMode.Disabled =>
        (false, None)
      case TlsMode.Enabled(advancedConfig) =>
        (true, advancedConfig.map(TlsAdvancedConfig.toGlide))
    }
}

object TlsMode {

  /** TLS is disabled - no encryption */
  case object Disabled extends TlsMode

  /** TLS is enabled with optional advanced configuration
    *
    * @param advancedConfig Optional advanced TLS settings (custom certs, insecure mode, etc.)
    */
  final case class Enabled(advancedConfig: Option[TlsAdvancedConfig] = None)
      extends TlsMode

  /** Convenience constructor for enabled TLS with default settings */
  val enabled: TlsMode = Enabled(None)

  /** Convenience constructor for disabled TLS */
  val disabled: TlsMode = Disabled
}

/** Advanced TLS configuration options
  *
  * @param rootCertificates Optional custom root certificates (PEM format as bytes)
  * @param useInsecureTLS Whether to skip certificate validation (dangerous - only for testing!)
  */
final case class TlsAdvancedConfig(
    rootCertificates: Option[Array[Byte]] = None,
    useInsecureTLS: Boolean = false
)

object TlsAdvancedConfig {

  private[valkey4cats] def toGlide(
      config: TlsAdvancedConfig
  ): G.TlsAdvancedConfiguration = {
    val builder = G.TlsAdvancedConfiguration.builder()

    config.rootCertificates.foreach(builder.rootCertificates)
    builder.useInsecureTLS(config.useInsecureTLS)

    builder.build()
  }

  /** Create config with custom root certificates (PEM bytes) */
  def withRootCertificates(certs: Array[Byte]): TlsAdvancedConfig =
    TlsAdvancedConfig(rootCertificates = Some(certs))

  /** Create config with root certificates from a PEM-encoded string */
  def fromCertString(pem: String): TlsAdvancedConfig =
    withRootCertificates(pem.getBytes(StandardCharsets.UTF_8))

  /** Load root certificates from a PEM file on disk */
  def fromCertFile[F[_]: Sync](path: Path): F[TlsAdvancedConfig] =
    Sync[F].blocking(withRootCertificates(Files.readAllBytes(path)))

  /** Create config for insecure TLS (skip certificate validation - testing only!) */
  def insecure: TlsAdvancedConfig =
    TlsAdvancedConfig(useInsecureTLS = true)
}
