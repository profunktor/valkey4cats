package dev.profunktor.valkey4cats.model

import cats.{ApplicativeThrow, FlatMap}
import cats.syntax.all.*
import com.comcast.ip4s.{Host, Port}
import glide.api.models.configuration as G
import scala.concurrent.duration.FiniteDuration

/** Configuration for Valkey cluster client */
sealed abstract class ValkeyClusterConfig {

  private[model] def common: CommonConfig

  /** Whether to refresh cluster topology from initial seed nodes (advanced) */
  def refreshTopologyFromInitialNodes: Option[Boolean]

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
      refreshTopologyFromInitialNodes: Option[Boolean] =
        this.refreshTopologyFromInitialNodes
  ): ValkeyClusterConfig =
    ValkeyClusterConfig.unsafeCreate(common, refreshTopologyFromInitialNodes)

  /** Convert to Glide's GlideClusterClientConfiguration */
  private[valkey4cats] def toGlide: G.GlideClusterClientConfiguration = {
    val builder = G.GlideClusterClientConfiguration.builder()
    val tlsAdvancedConfig = common.applyToGlideBuilder(builder)
    if (
      common.connectionTimeout.isDefined ||
      refreshTopologyFromInitialNodes.isDefined ||
      tlsAdvancedConfig.isDefined
    ) {
      val advancedBuilder = G.AdvancedGlideClusterClientConfiguration.builder()
      common.connectionTimeout.foreach(timeout =>
        advancedBuilder.connectionTimeout(timeout.toMillis.toInt)
      )
      refreshTopologyFromInitialNodes.foreach(
        advancedBuilder.refreshTopologyFromInitialNodes
      )
      tlsAdvancedConfig.foreach(advancedBuilder.tlsAdvancedConfiguration)
      val _ = builder.advancedConfiguration(advancedBuilder.build())
    }
    builder.build()
  }

  def addAddress(host: Host, port: Port): ValkeyClusterConfig =
    copy(common = common.addAddress(host, port))

  def addAddress(host: String, port: Int): Either[String, ValkeyClusterConfig] =
    common.addAddress(host, port).map(c => copy(common = c))

  def addAddress(nodeAddress: NodeAddress): ValkeyClusterConfig =
    copy(common = common.addAddress(nodeAddress))

  def withTlsMode(mode: TlsMode): ValkeyClusterConfig =
    copy(common = common.withTlsMode(mode))

  def withTlsEnabled: ValkeyClusterConfig =
    copy(common = common.withTlsEnabled)

  def withTlsAdvanced(config: TlsAdvancedConfig): ValkeyClusterConfig =
    copy(common = common.withTlsAdvanced(config))

  def withTlsDisabled: ValkeyClusterConfig =
    copy(common = common.withTlsDisabled)

  def withRequestTimeout(
      timeout: FiniteDuration
  ): Either[String, ValkeyClusterConfig] =
    common.withRequestTimeout(timeout).map(c => copy(common = c))

  def withCredentials(creds: ServerCredentials): ValkeyClusterConfig =
    copy(common = common.withCredentials(creds))

  def withPassword(password: String): ValkeyClusterConfig =
    copy(common = common.withPassword(password))

  def withReadFrom(strategy: ReadFromStrategy): ValkeyClusterConfig =
    copy(common = common.withReadFrom(strategy))

  def withReconnectStrategy(strategy: BackOffStrategy): ValkeyClusterConfig =
    copy(common = common.withReconnectStrategy(strategy))

  def withClientName(name: String): ValkeyClusterConfig =
    copy(common = common.withClientName(name))

  def withInflightRequestsLimit(
      limit: Int
  ): Either[String, ValkeyClusterConfig] =
    common.withInflightRequestsLimit(limit).map(c => copy(common = c))

  def withConnectionTimeout(
      timeout: FiniteDuration
  ): Either[String, ValkeyClusterConfig] =
    common.withConnectionTimeout(timeout).map(c => copy(common = c))

  def withLibName(name: String): ValkeyClusterConfig =
    copy(common = common.withLibName(name))

  def withLazyConnectEnabled: ValkeyClusterConfig =
    copy(common = common.withLazyConnectEnabled)

  def withLazyConnectDisabled: ValkeyClusterConfig =
    copy(common = common.withLazyConnectDisabled)

  def withClientAZ(az: String): ValkeyClusterConfig =
    copy(common = common.withClientAZ(az))

  def withRefreshTopologyFromInitialNodesEnabled: ValkeyClusterConfig =
    copy(refreshTopologyFromInitialNodes = Some(true))

  def withRefreshTopologyFromInitialNodesDisabled: ValkeyClusterConfig =
    copy(refreshTopologyFromInitialNodes = Some(false))
}

object ValkeyClusterConfig {

  private final case class ValkeyClusterConfigImpl(
      common: CommonConfig,
      refreshTopologyFromInitialNodes: Option[Boolean] = None
  ) extends ValkeyClusterConfig

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
      refreshTopologyFromInitialNodes: Option[Boolean] = None
  ): Either[String, ValkeyClusterConfig] = {
    val extraErrors = List.newBuilder[String]
    if (addresses.isEmpty)
      extraErrors += "At least one address is required for cluster configuration"
    val baseErrors = CommonConfig
      .validate(
        addresses,
        requestTimeout,
        connectionTimeout,
        inflightRequestsLimit
      )
      .filter(!_.contains("At least one address"))
    val allErrors = extraErrors.result() ++ baseErrors
    if (allErrors.nonEmpty) Left(allErrors.mkString("; "))
    else
      Right(
        ValkeyClusterConfigImpl(
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
            clientAZ
          ),
          refreshTopologyFromInitialNodes
        )
      )
  }

  private[model] def unsafeCreate(
      common: CommonConfig,
      refreshTopologyFromInitialNodes: Option[Boolean] = None
  ): ValkeyClusterConfig =
    ValkeyClusterConfigImpl(common, refreshTopologyFromInitialNodes)

  def fromUris[F[_]: ApplicativeThrow: FlatMap](
      uris: List[String]
  ): F[ValkeyClusterConfig] = {
    if (uris.isEmpty) {
      ApplicativeThrow[F].raiseError(
        new IllegalArgumentException(
          "At least one URI is required for cluster configuration"
        )
      )
    } else {
      uris
        .traverse(uri =>
          ApplicativeThrow[F].fromEither(ValkeyUri.fromString(uri))
        )
        .flatMap { parsedUris =>
          validateConsistentUris(parsedUris) match {
            case Left(error) =>
              ApplicativeThrow[F].raiseError(
                new IllegalArgumentException(error)
              )
            case Right(_) =>
              val first = parsedUris.head
              val allAddresses =
                parsedUris.map(uri => NodeAddress(uri.host, uri.port))
              ApplicativeThrow[F].pure(
                unsafeCreate(
                  CommonConfig.unsafeCreate(
                    addresses = allAddresses,
                    tlsMode =
                      if (first.useTls) TlsMode.enabled else TlsMode.disabled,
                    credentials = first.credentials
                  )
                )
              )
          }
        }
    }
  }

  private def validateConsistentUris(
      uris: List[ValkeyUri]
  ): Either[String, Unit] = {
    if (uris.isEmpty) Left("No URIs provided")
    else {
      val first = uris.head
      val inconsistent = uris.tail.find(!first.isConsistentWith(_))
      inconsistent match {
        case Some(uri) =>
          if (first.useTls != uri.useTls)
            Left(
              s"Inconsistent TLS settings: some URIs use TLS (rediss://) and others don't (redis://). " +
                "All cluster seed nodes must have the same TLS setting."
            )
          else
            Left(
              "Inconsistent credentials: all cluster seed nodes must have the same authentication settings."
            )
        case None => Right(())
      }
    }
  }

  def make[F[_]: ApplicativeThrow](
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
      refreshTopologyFromInitialNodes: Option[Boolean] = None
  ): F[ValkeyClusterConfig] =
    ApplicativeThrow[F].fromEither(
      apply(
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
        refreshTopologyFromInitialNodes
      ).left.map(msg => new IllegalArgumentException(msg))
    )

  def builder(
      host: Host,
      port: Port = NodeAddress.DefaultPort
  ): ValkeyClusterConfig =
    unsafeCreate(
      CommonConfig.unsafeCreate(addresses = List(NodeAddress(host, port)))
    )

  def builder(host: String, port: Int): Either[String, ValkeyClusterConfig] =
    NodeAddress
      .fromString(host, port)
      .map(addr =>
        unsafeCreate(CommonConfig.unsafeCreate(addresses = List(addr)))
      )
}
