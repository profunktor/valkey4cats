package dev.profunktor.valkey4cats.model

import cats.ApplicativeThrow
import com.comcast.ip4s.{Host, Port}

import java.net.URI
import scala.util.Try

/** Represents a parsed Valkey/Redis URI
  *
  * Supports URI schemes:
  * - valkey:// (native Valkey, standard connection)
  * - valkeys:// (native Valkey, TLS connection)
  * - redis:// (legacy compatibility, standard connection)
  * - rediss:// (legacy compatibility, TLS connection)
  */
sealed abstract class ValkeyUri {

  /** The URI scheme (valkey, valkeys, redis, rediss) */
  def scheme: ValkeyUri.Scheme

  /** The server host */
  def host: Host

  /** The server port */
  def port: Port

  /** Optional authentication credentials */
  def credentials: Option[ServerCredentials]

  /** Optional database number (0-15) */
  def database: Option[DatabaseId]

  /** Whether this URI requires TLS */
  def useTls: Boolean = scheme.requiresTls

  /** Convert to java.net.URI */
  def toURI: URI = {
    val auth = credentials match {
      case Some(p: ServerCredentials.Password) => s":${p.password}@"
      case Some(up: ServerCredentials.UsernamePassword) =>
        s"${up.username}:${up.password}@"
      case Some(_: ServerCredentials.IamAuth) =>
        // IAM auth cannot be represented in URI format
        ""
      case None => ""
    }
    val db = database.map(d => s"/${d.value}").getOrElse("")
    new URI(s"${scheme.name}://$auth$host:${port.value}$db")
  }

  /** Check if this URI is consistent with another for cluster configuration.
    *
    * Two URIs are considered consistent if they have:
    * - Same TLS setting (both use TLS or both don't)
    * - Same credentials (both have same auth or both have none)
    *
    * This is used to validate that all cluster seed nodes have compatible settings.
    *
    * @param other The other URI to compare against
    * @return true if URIs are consistent for clustering
    */
  def isConsistentWith(other: ValkeyUri): Boolean =
    this.useTls == other.useTls && this.credentials == other.credentials
}

object ValkeyUri {

  private final case class ValkeyUriImpl(
      scheme: Scheme,
      host: Host,
      port: Port,
      credentials: Option[ServerCredentials] = None,
      database: Option[DatabaseId] = None
  ) extends ValkeyUri

  /** Create a ValkeyUri */
  def apply(
      scheme: Scheme,
      host: Host,
      port: Port,
      credentials: Option[ServerCredentials] = None,
      database: Option[DatabaseId] = None
  ): ValkeyUri =
    ValkeyUriImpl(scheme, host, port, credentials, database)

  /** Pattern matching support */
  def unapply(
      uri: ValkeyUri
  ): Option[
    (Scheme, Host, Port, Option[ServerCredentials], Option[DatabaseId])
  ] =
    Some((uri.scheme, uri.host, uri.port, uri.credentials, uri.database))

  /** URI scheme variants */
  sealed trait Scheme { self =>

    def name: String = self match {
      case Scheme.Valkey  => "valkey"
      case Scheme.Valkeys => "valkeys"
      case Scheme.Redis   => "redis"
      case Scheme.Rediss  => "rediss"
    }

    def requiresTls: Boolean = self match {
      case Scheme.Valkey  => false
      case Scheme.Valkeys => true
      case Scheme.Redis   => false
      case Scheme.Rediss  => true
    }
  }

  object Scheme {
    case object Valkey extends Scheme
    case object Valkeys extends Scheme
    case object Redis extends Scheme
    case object Rediss extends Scheme

    def fromString(s: String): Either[String, Scheme] = s.toLowerCase match {
      case "valkey"  => Right(Valkey)
      case "valkeys" => Right(Valkeys)
      case "redis"   => Right(Redis)
      case "rediss"  => Right(Rediss)
      case other =>
        Left(
          s"Invalid scheme '$other'. Must be one of: valkey, valkeys, redis, rediss"
        )
    }
  }

  /** Parse a URI string into a ValkeyUri (unsafe - throws on invalid input)
    *
    * @param uriString The URI string to parse
    * @return The parsed ValkeyUri
    */
  def unsafeFromString(uriString: String): ValkeyUri = {
    val parsed = new URI(uriString)

    val scheme = Scheme.fromString(parsed.getScheme).getOrElse {
      throw new IllegalArgumentException(
        s"Invalid scheme '${parsed.getScheme}'. Must be one of: valkey, valkeys, redis, rediss"
      )
    }

    val hostStr = Option(parsed.getHost).getOrElse("localhost")
    val host = Host.fromString(hostStr).getOrElse {
      throw new IllegalArgumentException(s"Invalid host: $hostStr")
    }

    val portInt =
      if (parsed.getPort > 0) parsed.getPort else NodeAddress.DefaultPort.value
    val port = Port.fromInt(portInt).getOrElse {
      throw new IllegalArgumentException(s"Invalid port: $portInt")
    }

    // Parse credentials from userInfo
    val credentials: Option[ServerCredentials] =
      Option(parsed.getUserInfo).flatMap { userInfo =>
        userInfo.split(":", 2) match {
          case Array("", password) =>
            Some(ServerCredentials.password(password))
          case Array(username, password) =>
            Some(ServerCredentials.usernamePassword(username, password))
          case _ => None
        }
      }

    // Parse database from path
    val database: Option[DatabaseId] = Option(parsed.getPath)
      .filter(_.nonEmpty)
      .map(_.stripPrefix("/"))
      .filter(_.nonEmpty)
      .map { db =>
        db.toIntOption match {
          case Some(id) =>
            DatabaseId(id) match {
              case Right(dbId) => dbId
              case Left(msg) =>
                throw new IllegalArgumentException(
                  s"Invalid database in URI: $msg"
                )
            }
          case None =>
            throw new IllegalArgumentException(
              s"Invalid database path segment '$db': must be a number between ${DatabaseId.MinValue} and ${DatabaseId.MaxValue}"
            )
        }
      }

    ValkeyUri(
      scheme = scheme,
      host = host,
      port = port,
      credentials = credentials,
      database = database
    )
  }

  /** Parse a URI string into a ValkeyUri (safe - returns Either)
    *
    * @param uriString The URI string to parse
    * @return Either an error or the parsed URI
    */
  def fromString(uriString: String): Either[Throwable, ValkeyUri] =
    Try(unsafeFromString(uriString)).toEither

  def make[F[_]: ApplicativeThrow](uri: => String): F[ValkeyUri] =
    ApplicativeThrow[F].catchNonFatal(unsafeFromString(uri))

}
