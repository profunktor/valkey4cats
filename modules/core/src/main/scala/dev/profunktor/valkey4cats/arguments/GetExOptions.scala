package dev.profunktor.valkey4cats.arguments

/** Expiry options for GETEX command */
sealed trait GetExExpiry

object GetExExpiry {

  /** Set expiry in seconds */
  final case class Seconds(seconds: Long) extends GetExExpiry

  /** Set expiry in milliseconds */
  final case class Milliseconds(milliseconds: Long) extends GetExExpiry

  /** Set expiry as Unix timestamp in seconds */
  final case class UnixSeconds(timestamp: Long) extends GetExExpiry

  /** Set expiry as Unix timestamp in milliseconds */
  final case class UnixMilliseconds(timestamp: Long) extends GetExExpiry

  /** Remove the time to live associated with the key (PERSIST) */
  case object Persist extends GetExExpiry

}
