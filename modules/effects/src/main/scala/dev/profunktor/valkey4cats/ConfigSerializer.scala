package dev.profunktor.valkey4cats

import connection_request as proto
import dev.profunktor.valkey4cats.model.*

private[valkey4cats] object ConfigSerializer:

  def serialize(uri: ValkeyUri): Array[Byte] =
    val config = ValkeyClientConfig.fromUri(uri)
    serializeStandalone(config)

  def serializeStandalone(config: ValkeyClientConfig): Array[Byte] =
    buildRequest(
      addresses = config.addresses,
      tlsMode = config.tlsMode,
      clusterMode = false,
      requestTimeout = config.requestTimeout,
      readFrom = config.readFrom,
      reconnectStrategy = config.reconnectStrategy,
      credentials = config.credentials,
      databaseId = config.databaseId,
      protocolVersion = config.protocolVersion,
      clientName = config.clientName,
      inflightRequestsLimit = config.inflightRequestsLimit,
      clientAZ = config.clientAZ,
      connectionTimeout = config.connectionTimeout,
      lazyConnect = config.lazyConnect,
      libName = config.libName
    ).toByteArray

  def serializeCluster(config: ValkeyClusterConfig): Array[Byte] =
    buildRequest(
      addresses = config.addresses,
      tlsMode = config.tlsMode,
      clusterMode = true,
      requestTimeout = config.requestTimeout,
      readFrom = config.readFrom,
      reconnectStrategy = config.reconnectStrategy,
      credentials = config.credentials,
      databaseId = None,
      protocolVersion = config.protocolVersion,
      clientName = config.clientName,
      inflightRequestsLimit = config.inflightRequestsLimit,
      clientAZ = config.clientAZ,
      connectionTimeout = config.connectionTimeout,
      lazyConnect = config.lazyConnect,
      libName = config.libName
    ).toByteArray

  private def buildRequest(
      addresses: List[NodeAddress],
      tlsMode: TlsMode,
      clusterMode: Boolean,
      requestTimeout: Option[scala.concurrent.duration.FiniteDuration],
      readFrom: Option[ReadFromStrategy],
      reconnectStrategy: Option[BackOffStrategy],
      credentials: Option[ServerCredentials],
      databaseId: Option[DatabaseId],
      protocolVersion: ProtocolVersion,
      clientName: Option[String],
      inflightRequestsLimit: Option[Int],
      clientAZ: Option[String],
      connectionTimeout: Option[scala.concurrent.duration.FiniteDuration],
      lazyConnect: Option[Boolean],
      libName: Option[String]
  ): proto.ConnectionRequest =
    proto.ConnectionRequest(
      addresses = addresses.map(a => proto.NodeAddress(a.host.toString, a.port.value)),
      tlsMode = mapTlsMode(tlsMode),
      clusterModeEnabled = clusterMode,
      requestTimeout = requestTimeout.fold(0)(_.toMillis.toInt),
      readFrom = readFrom.fold(proto.ReadFrom.Primary)(mapReadFrom),
      connectionRetryStrategy = reconnectStrategy.map(mapRetryStrategy),
      authenticationInfo = credentials.map(mapCredentials),
      databaseId = databaseId.fold(0)(_.value),
      protocol = protocolVersion match
        case ProtocolVersion.RESP2 => proto.ProtocolVersion.RESP2
        case ProtocolVersion.RESP3 => proto.ProtocolVersion.RESP3,
      clientName = clientName.getOrElse(""),
      inflightRequestsLimit = inflightRequestsLimit.getOrElse(0),
      clientAz = clientAZ.getOrElse(""),
      connectionTimeout = connectionTimeout.fold(0)(_.toMillis.toInt),
      lazyConnect = lazyConnect.getOrElse(false),
      libName = libName.getOrElse("")
    )

  private def mapTlsMode(mode: TlsMode): proto.TlsMode = mode match
    case TlsMode.Disabled => proto.TlsMode.NoTls
    case TlsMode.Enabled(Some(adv)) if adv.useInsecureTLS => proto.TlsMode.InsecureTls
    case TlsMode.Enabled(_) => proto.TlsMode.SecureTls

  private def mapReadFrom(strategy: ReadFromStrategy): proto.ReadFrom = strategy match
    case ReadFromStrategy.Primary => proto.ReadFrom.Primary
    case ReadFromStrategy.PreferReplica => proto.ReadFrom.PreferReplica
    case ReadFromStrategy.AzAffinity => proto.ReadFrom.AZAffinity
    case ReadFromStrategy.AzAffinityReplicasAndPrimary => proto.ReadFrom.AZAffinityReplicasAndPrimary

  private def mapRetryStrategy(strategy: BackOffStrategy): proto.ConnectionRetryStrategy = strategy match
    case BackOffStrategy.FixedDelay(retries, factor) =>
      proto.ConnectionRetryStrategy(retries, factor.toMillis.toInt, 1)
    case BackOffStrategy.ExponentialBackoff(retries, base, expBase, jitter) =>
      proto.ConnectionRetryStrategy(retries, base.toMillis.toInt, expBase, Some(jitter))

  private def mapCredentials(creds: ServerCredentials): proto.AuthenticationInfo = creds match
    case p: ServerCredentials.Password =>
      proto.AuthenticationInfo(password = p.password)
    case up: ServerCredentials.UsernamePassword =>
      proto.AuthenticationInfo(password = up.password, username = up.username)
    case iam: ServerCredentials.IamAuth =>
      proto.AuthenticationInfo(
        iamCredentials = Some(proto.IamCredentials(
          clusterName = iam.config.clusterName,
          region = iam.config.region,
          serviceType = iam.config.service match
            case ServiceType.ElastiCache => proto.ServiceType.ELASTICACHE
            case ServiceType.MemoryDB => proto.ServiceType.MEMORYDB
        ))
      )
