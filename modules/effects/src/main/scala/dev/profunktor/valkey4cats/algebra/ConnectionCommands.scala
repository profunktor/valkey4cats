package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.model.ValkeyResponse

/** Connection management commands (PING, ECHO, CLIENT ID, CLIENT GETNAME, SELECT) */
trait ConnectionCommands[F[_], K, V] {

  /** Test connectivity. Returns "PONG" on success. */
  def ping: F[ValkeyResponse[String]]

  /** Test connectivity with a custom message. Returns the same message. */
  def ping(message: V): F[ValkeyResponse[V]]

  /** Echo the given message back. Useful for testing. */
  def echo(message: V): F[ValkeyResponse[V]]

  /** Return the unique numeric ID of the current connection. */
  def clientId: F[ValkeyResponse[Long]]

  /** Return the name of the current connection as set by CLIENT SETNAME, or None if unset. */
  def clientGetName: F[ValkeyResponse[Option[String]]]

  /** Switch to a different database. The index must be between 0 and 15.
    * Only available on standalone (non-cluster) clients.
    */
  def select(index: Long): F[ValkeyResponse[Unit]]
}
