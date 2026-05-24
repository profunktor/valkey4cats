package dev.profunktor.valkey4cats.model

import com.comcast.ip4s.{Host, Port}
import glide.api.models.configuration as G

import scala.concurrent.duration.{Duration, FiniteDuration}

/** Shared connection configuration for both standalone and cluster Valkey clients.
  *
  * Use the validated `apply` constructor on the companion object, then chain `with*` / `add*` builder methods.
  */
sealed abstract class CommonConfig {

  def addresses: List[NodeAddress]
  def tlsMode: TlsMode
  def requestTimeout: Option[FiniteDuration]
  def credentials: Option[ServerCredentials]
  def readFrom: Option[ReadFromStrategy]
  def reconnectStrategy: Option[BackOffStrategy]
  def clientName: Option[String]
  def protocolVersion: ProtocolVersion
  def inflightRequestsLimit: Option[Int]
  def connectionTimeout: Option[FiniteDuration]
  def libName: Option[String]
  def lazyConnect: Option[Boolean]
  def clientAZ: Option[String]
  def clientSideCache: Option[ClientSideCacheConfig]

  private[model] def copy(
      addresses: List[NodeAddress] = this.addresses,
      tlsMode: TlsMode = this.tlsMode,
      requestTimeout: Option[FiniteDuration] = this.requestTimeout,
      credentials: Option[ServerCredentials] = this.credentials,
      readFrom: Option[ReadFromStrategy] = this.readFrom,
      reconnectStrategy: Option[BackOffStrategy] = this.reconnectStrategy,
      clientName: Option[String] = this.clientName,
      protocolVersion: ProtocolVersion = this.protocolVersion,
      inflightRequestsLimit: Option[Int] = this.inflightRequestsLimit,
      connectionTimeout: Option[FiniteDuration] = this.connectionTimeout,
      libName: Option[String] = this.libName,
      lazyConnect: Option[Boolean] = this.lazyConnect,
      clientAZ: Option[String] = this.clientAZ,
      clientSideCache: Option[ClientSideCacheConfig] = this.clientSideCache
  ): CommonConfig =
    CommonConfig.unsafeCreate(
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
      clientAZ,
      clientSideCache
    )

  private[model] def applyToGlideBuilder(
      builder: G.BaseClientConfiguration.BaseClientConfigurationBuilder[?, ?]
  ): Option[G.TlsAdvancedConfiguration] = {
    addresses.foreach(addr => builder.address(addr.toGlide))
    val (useTls, tlsAdvancedConfig) = tlsMode.toGlide
    builder.useTLS(useTls)
    requestTimeout.foreach(d => builder.requestTimeout(d.toMillis.toInt))
    credentials.foreach(c => builder.credentials(c.toGlide))
    readFrom.foreach(r => builder.readFrom(r.toGlide))
    reconnectStrategy.foreach(s => builder.reconnectStrategy(s.toGlide))
    clientName.foreach(builder.clientName(_))
    builder.protocol(protocolVersion.toGlide)
    inflightRequestsLimit.foreach(builder.inflightRequestsLimit(_))
    libName.foreach(builder.libName(_))
    lazyConnect.foreach(builder.lazyConnect(_))
    clientAZ.foreach(builder.clientAZ(_))
    tlsAdvancedConfig
  }

  def withAddress(
      host: Host,
      port: Port = NodeAddress.DefaultPort
  ): CommonConfig =
    copy(addresses = List(NodeAddress(host, port)))

  def withAddress(host: String, port: Int): Either[String, CommonConfig] =
    NodeAddress.fromString(host, port).map(addr => copy(addresses = List(addr)))

  def addAddress(nodeAddress: NodeAddress): CommonConfig =
    copy(addresses = addresses :+ nodeAddress)

  def addAddress(host: Host, port: Port): CommonConfig =
    addAddress(NodeAddress(host, port))

  def addAddress(host: String, port: Int): Either[String, CommonConfig] =
    NodeAddress.fromString(host, port).map(addAddress)

  def withTlsMode(mode: TlsMode): CommonConfig =
    copy(tlsMode = mode)

  def withTlsEnabled: CommonConfig =
    copy(tlsMode = TlsMode.enabled)

  def withTlsAdvanced(config: TlsAdvancedConfig): CommonConfig =
    copy(tlsMode = TlsMode.Enabled(Some(config)))

  def withTlsDisabled: CommonConfig =
    copy(tlsMode = TlsMode.disabled)

  def withRequestTimeout(
      timeout: FiniteDuration
  ): Either[String, CommonConfig] =
    if (timeout > Duration.Zero) Right(copy(requestTimeout = Some(timeout)))
    else Left(s"Request timeout must be positive, got: $timeout")

  def withCredentials(creds: ServerCredentials): CommonConfig =
    copy(credentials = Some(creds))

  def withPassword(password: String): CommonConfig =
    copy(credentials = Some(ServerCredentials.password(password)))

  def withReadFrom(strategy: ReadFromStrategy): CommonConfig =
    copy(readFrom = Some(strategy))

  def withReconnectStrategy(strategy: BackOffStrategy): CommonConfig =
    copy(reconnectStrategy = Some(strategy))

  def withClientName(name: String): CommonConfig =
    copy(clientName = Some(name))

  def withInflightRequestsLimit(limit: Int): Either[String, CommonConfig] =
    if (limit > 0) Right(copy(inflightRequestsLimit = Some(limit)))
    else Left(s"Inflight requests limit must be positive, got: $limit")

  def withConnectionTimeout(
      timeout: FiniteDuration
  ): Either[String, CommonConfig] =
    if (timeout > Duration.Zero) Right(copy(connectionTimeout = Some(timeout)))
    else Left(s"Connection timeout must be positive, got: $timeout")

  def withLibName(name: String): CommonConfig =
    copy(libName = Some(name))

  def withLazyConnectEnabled: CommonConfig =
    copy(lazyConnect = Some(true))

  def withLazyConnectDisabled: CommonConfig =
    copy(lazyConnect = Some(false))

  def withClientAZ(az: String): CommonConfig =
    copy(clientAZ = Some(az))

  def withClientSideCache(config: ClientSideCacheConfig): CommonConfig =
    copy(clientSideCache = Some(config))

  def withoutClientSideCache: CommonConfig =
    copy(clientSideCache = None)
}

object CommonConfig {

  private final case class CommonConfigImpl(
      addresses: List[NodeAddress],
      tlsMode: TlsMode = TlsMode.Disabled,
      requestTimeout: Option[FiniteDuration] = None,
      credentials: Option[ServerCredentials] = None,
      readFrom: Option[ReadFromStrategy] = None,
      reconnectStrategy: Option[BackOffStrategy] = None,
      clientName: Option[String] = None,
      protocolVersion: ProtocolVersion = ProtocolVersion.RESP3,
      inflightRequestsLimit: Option[Int] = None,
      connectionTimeout: Option[FiniteDuration] = None,
      libName: Option[String] = None,
      lazyConnect: Option[Boolean] = None,
      clientAZ: Option[String] = None,
      clientSideCache: Option[ClientSideCacheConfig] = None
  ) extends CommonConfig

  def apply(
      addresses: List[NodeAddress],
      tlsMode: TlsMode = TlsMode.Disabled,
      requestTimeout: Option[FiniteDuration] = None,
      credentials: Option[ServerCredentials] = None,
      readFrom: Option[ReadFromStrategy] = None,
      reconnectStrategy: Option[BackOffStrategy] = None,
      clientName: Option[String] = None,
      protocolVersion: ProtocolVersion = ProtocolVersion.RESP3,
      inflightRequestsLimit: Option[Int] = None,
      connectionTimeout: Option[FiniteDuration] = None,
      libName: Option[String] = None,
      lazyConnect: Option[Boolean] = None,
      clientAZ: Option[String] = None,
      clientSideCache: Option[ClientSideCacheConfig] = None
  ): Either[String, CommonConfig] = {
    val errors = validate(
      addresses,
      requestTimeout,
      connectionTimeout,
      inflightRequestsLimit
    )
    if (errors.nonEmpty) Left(errors.mkString("; "))
    else
      Right(
        CommonConfigImpl(
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
          clientAZ,
          clientSideCache
        )
      )
  }

  private[model] def unsafeCreate(
      addresses: List[NodeAddress],
      tlsMode: TlsMode = TlsMode.Disabled,
      requestTimeout: Option[FiniteDuration] = None,
      credentials: Option[ServerCredentials] = None,
      readFrom: Option[ReadFromStrategy] = None,
      reconnectStrategy: Option[BackOffStrategy] = None,
      clientName: Option[String] = None,
      protocolVersion: ProtocolVersion = ProtocolVersion.RESP3,
      inflightRequestsLimit: Option[Int] = None,
      connectionTimeout: Option[FiniteDuration] = None,
      libName: Option[String] = None,
      lazyConnect: Option[Boolean] = None,
      clientAZ: Option[String] = None,
      clientSideCache: Option[ClientSideCacheConfig] = None
  ): CommonConfig =
    CommonConfigImpl(
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
      clientAZ,
      clientSideCache
    )

  private[model] def validate(
      addresses: List[NodeAddress],
      requestTimeout: Option[FiniteDuration],
      connectionTimeout: Option[FiniteDuration],
      inflightRequestsLimit: Option[Int]
  ): List[String] = {
    val errors = List.newBuilder[String]
    if (addresses.isEmpty) errors += "At least one address is required"
    requestTimeout.foreach { t =>
      if (t <= Duration.Zero)
        errors += s"Request timeout must be positive, got: $t"
    }
    connectionTimeout.foreach { t =>
      if (t <= Duration.Zero)
        errors += s"Connection timeout must be positive, got: $t"
    }
    inflightRequestsLimit.foreach { l =>
      if (l <= 0) errors += s"Inflight requests limit must be positive, got: $l"
    }
    errors.result()
  }
}
