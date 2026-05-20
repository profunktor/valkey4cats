package dev.profunktor.valkey4cats.arguments

/** Expiry options for the HGETEX command */
sealed trait HGetExExpiry

object HGetExExpiry {

  /** Set field expiry in seconds */
  case class Seconds(value: Long) extends HGetExExpiry

  /** Set field expiry in milliseconds */
  case class Milliseconds(value: Long) extends HGetExExpiry

  /** Set field expiry as Unix timestamp in seconds */
  case class UnixSeconds(value: Long) extends HGetExExpiry

  /** Set field expiry as Unix timestamp in milliseconds */
  case class UnixMilliseconds(value: Long) extends HGetExExpiry

  /** Remove the existing TTL on the field */
  case object Persist extends HGetExExpiry
}
