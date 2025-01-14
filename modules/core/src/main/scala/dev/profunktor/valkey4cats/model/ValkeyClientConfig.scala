package dev.profunktor.valkey4cats.model

import cats.ApplicativeThrow
import com.comcast.ip4s.{Host, Port}
import glide.api.models.configuration as G

import scala.concurrent.duration.FiniteDuration

/** Configuration for standalone Valkey client */
sealed abstract class ValkeyClientConfig {

  private[model] def common: CommonConfig

  /** Optional database ID (0-15, default 0) */
  def databaseId: Option[DatabaseId]

  def addresses: List[NodeAddress] = common.addresses
  def tlsMode: TlsMode = common.tlsMode
  def requestTimeout: Option[FiniteDuration] = common.requestTimeout
  def credentials: Option[ServerCredentials] = common.credentials
  def readFrom: Option[ReadFromStrategy] = common.readFrom
  def reconnectStrategy: Option[BackOffStrategy] = common.reconnectStrategy
  def clientName: Option[String] = common.clientName
  def protocolVersion: ProtocolVersion = common.protocolVersion
  def inflightRequestsLimit: Option[Int] = common.inflightRequestsLimit
  def connectionTimeout: Option[FiniteDuration] = common.connectionTimeout
  def libName: Option[String] = common.libName
  def lazyConnect: Option[Boolean] = common.lazyConnect
  def clientAZ: Option[String] = common.clientAZ

  private[model] def copy(
      common: CommonConfig = this.common,
      databaseId: Option[DatabaseId] = this.databaseId
  ): ValkeyClientConfig =
    ValkeyClientConfig.unsafeCreate(common, databaseId)

  /** Convert to Glide's GlideClientConfiguration */
  private[valkey4cats] def toGlide: G.GlideClientConfiguration = {
    val builder = G.GlideClientConfiguration.builder()
    val tlsAdvancedConfig = common.applyToGlideBuilder(builder)
    databaseId.foreach(id => builder.databaseId(id.value))
    if (common.connectionTimeout.isDefined || tlsAdvancedConfig.isDefined) {
      val advancedBuilder = G.AdvancedGlideClientConfiguration.builder()
      common.connectionTimeout.foreach(timeout =>
        advancedBuilder.connectionTimeout(timeout.toMillis.toInt)
      )
      tlsAdvancedConfig.foreach(advancedBuilder.tlsAdvancedConfiguration)
      val _ = builder.advancedConfiguration(advancedBuilder.build())
    }
    builder.build()
  }

  /** Set address from ip4s types (always valid by construction) */
  def withAddress(
      host: Host,
      port: Port = NodeAddress.DefaultPort
  ): ValkeyClientConfig =
    copy(common = common.withAddress(host, port))

  /** Set address from raw strings (validated) */
  def withAddress(host: String, port: Int): Either[String, ValkeyClientConfig] =
    common.withAddress(host, port).map(c => copy(common = c))

  def addAddress(nodeAddress: NodeAddress): ValkeyClientConfig =
    copy(common = common.addAddress(nodeAddress))

  def addAddress(host: Host, port: Port): ValkeyClientConfig =
    copy(common = common.addAddress(host, port))

  def addAddress(host: String, port: Int): Either[String, ValkeyClientConfig] =
    common.addAddress(host, port).map(c => copy(common = c))

  def withTlsMode(mode: TlsMode): ValkeyClientConfig =
    copy(common = common.withTlsMode(mode))

  def withTlsEnabled: ValkeyClientConfig =
    copy(common = common.withTlsEnabled)

  def withTlsAdvanced(config: TlsAdvancedConfig): ValkeyClientConfig =
    copy(common = common.withTlsAdvanced(config))

  def withTlsDisabled: ValkeyClientConfig =
    copy(common = common.withTlsDisabled)

  def withRequestTimeout(
      timeout: FiniteDuration
  ): Either[String, ValkeyClientConfig] =
    common.withRequestTimeout(timeout).map(c => copy(common = c))

  def withCredentials(creds: ServerCredentials): ValkeyClientConfig =
    copy(common = common.withCredentials(creds))

  def withPassword(password: String): ValkeyClientConfig =
    copy(common = common.withPassword(password))

  def withReadFrom(strategy: ReadFromStrategy): ValkeyClientConfig =
    copy(common = common.withReadFrom(strategy))

  def withReconnectStrategy(strategy: BackOffStrategy): ValkeyClientConfig =
    copy(common = common.withReconnectStrategy(strategy))

  /** Set database ID */
  def withDatabase(db: Int): Either[String, ValkeyClientConfig] =
    DatabaseId(db).map(id => copy(databaseId = Some(id)))

  /** Set database ID from a validated DatabaseId */
  def withDatabase(db: DatabaseId): ValkeyClientConfig =
    copy(databaseId = Some(db))

  def withClientName(name: String): ValkeyClientConfig =
    copy(common = common.withClientName(name))

  def withInflightRequestsLimit(
      limit: Int
  ): Either[String, ValkeyClientConfig] =
    common.withInflightRequestsLimit(limit).map(c => copy(common = c))

  def withConnectionTimeout(
      timeout: FiniteDuration
  ): Either[String, ValkeyClientConfig] =
    common.withConnectionTimeout(timeout).map(c => copy(common = c))

  def withLibName(name: String): ValkeyClientConfig =
    copy(common = common.withLibName(name))

  def withLazyConnectEnabled: ValkeyClientConfig =
    copy(common = common.withLazyConnectEnabled)

  def withLazyConnectDisabled: ValkeyClientConfig =
    copy(common = common.withLazyConnectDisabled)

  def withClientAZ(az: String): ValkeyClientConfig =
    copy(common = common.withClientAZ(az))
}

object ValkeyClientConfig {

  private final case class ValkeyClientConfigImpl(
      common: CommonConfig,
      databaseId: Option[DatabaseId] = None
  ) extends ValkeyClientConfig

  def apply(
      addresses: List[NodeAddress],
      tlsMode: TlsMode = TlsMode.Disabled,
      requestTimeout: Option[FiniteDuration] = None,
      credentials: Option[ServerCredentials] = None,
      readFrom: Option[ReadFromStrategy] = None,
      reconnectStrategy: Option[BackOffStrategy] = None,
      databaseId: Option[DatabaseId] = None,
      clientName: Option[String] = None,
      protocolVersion: ProtocolVersion = ProtocolVersion.RESP3,
      inflightRequestsLimit: Option[Int] = None,
      connectionTimeout: Option[FiniteDuration] = None,
      libName: Option[String] = None,
      lazyConnect: Option[Boolean] = None,
      clientAZ: Option[String] = None
  ): Either[String, ValkeyClientConfig] =
    CommonConfig(
      addresses,
      tlsMode,
      requestTimeout,
      credentials,
      readFrom,
      reconnectStrategy,
      clientName,
      protocolVersion,
      inflightRequestsLimit,
      connectionTimeout,
      libName,
      lazyConnect,
      clientAZ
    ).map(ValkeyClientConfigImpl(_, databaseId))

  private[model] def unsafeCreate(
      common: CommonConfig,
      databaseId: Option[DatabaseId] = None
  ): ValkeyClientConfig =
    ValkeyClientConfigImpl(common, databaseId)

  def fromUri(uri: ValkeyUri): ValkeyClientConfig =
    unsafeCreate(
      CommonConfig.unsafeCreate(
        addresses = List(NodeAddress(uri.host, uri.port)),
        tlsMode = if (uri.useTls) TlsMode.enabled else TlsMode.disabled,
        credentials = uri.credentials
      ),
      databaseId = uri.database
    )

  def fromUriString(uriString: String): Either[Throwable, ValkeyClientConfig] =
    ValkeyUri.fromString(uriString).map(fromUri)

  def fromUri[F[_]: ApplicativeThrow](uri: String): F[ValkeyClientConfig] =
    ApplicativeThrow[F].fromEither(fromUriString(uri))

  def make[F[_]: ApplicativeThrow](
      addresses: List[NodeAddress],
      tlsMode: TlsMode = TlsMode.Disabled,
      requestTimeout: Option[FiniteDuration] = None,
      credentials: Option[ServerCredentials] = None,
      readFrom: Option[ReadFromStrategy] = None,
      reconnectStrategy: Option[BackOffStrategy] = None,
      databaseId: Option[DatabaseId] = None,
      clientName: Option[String] = None,
      protocolVersion: ProtocolVersion = ProtocolVersion.RESP3,
      inflightRequestsLimit: Option[Int] = None,
      connectionTimeout: Option[FiniteDuration] = None,
      libName: Option[String] = None,
      lazyConnect: Option[Boolean] = None,
      clientAZ: Option[String] = None
  ): F[ValkeyClientConfig] =
    ApplicativeThrow[F].fromEither(
      apply(
        addresses,
        tlsMode,
        requestTimeout,
        credentials,
        readFrom,
        reconnectStrategy,
        databaseId,
        clientName,
        protocolVersion,
        inflightRequestsLimit,
        connectionTimeout,
        libName,
        lazyConnect,
        clientAZ
      ).left.map(msg => new IllegalArgumentException(msg))
    )

  private val localhostHost: Host =
    Host.fromString("localhost").get

  val localhost: ValkeyClientConfig = unsafeCreate(
    CommonConfig.unsafeCreate(
      addresses = List(NodeAddress(localhostHost, NodeAddress.DefaultPort))
    )
  )

  def builder: ValkeyClientConfig = localhost
}
