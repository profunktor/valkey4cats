package dev.profunktor.valkey4cats.model

import com.comcast.ip4s.*

/** Represents a Valkey server node address using ip4s types for validated host/port */
sealed abstract class NodeAddress {

  /** The hostname or IP address */
  def host: Host

  /** The port number */
  def port: Port
}

object NodeAddress {

  /** Default Valkey port */
  val DefaultPort: Port = port"6379" 

  private final case class NodeAddressImpl(
      host: Host,
      port: Port
  ) extends NodeAddress

  /** Create a NodeAddress from ip4s types (always valid by construction) */
  def apply(host: Host, port: Port): NodeAddress =
    NodeAddressImpl(host, port)

  /** Create a NodeAddress with default port */
  def apply(host: Host): NodeAddress =
    NodeAddressImpl(host, DefaultPort)

  /** Create from raw strings — returns Either for invalid host or port */
  def fromString(host: String, port: Int): Either[String, NodeAddress] =
    for {
      h <- Host.fromString(host).toRight(s"Invalid host: $host")
      p <- Port.fromInt(port).toRight(s"Invalid port: $port")
    } yield NodeAddressImpl(h, p)

  /** Create from raw host string with default port */
  def fromString(host: String): Either[String, NodeAddress] =
    Host
      .fromString(host)
      .toRight(s"Invalid host: $host")
      .map(NodeAddressImpl(_, DefaultPort))

  /** Pattern matching support */
  def unapply(addr: NodeAddress): Option[(Host, Port)] =
    Some((addr.host, addr.port))
}
