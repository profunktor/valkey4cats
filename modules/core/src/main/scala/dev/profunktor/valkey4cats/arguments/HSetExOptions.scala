package dev.profunktor.valkey4cats.arguments

/** Expiry options for the HSETEX command */
sealed trait ExpirySet

object ExpirySet {

  /** Set field expiry in seconds */
  case class Seconds(value: Long) extends ExpirySet

  /** Set field expiry in milliseconds */
  case class Milliseconds(value: Long) extends ExpirySet

  /** Set field expiry as Unix timestamp in seconds */
  case class UnixSeconds(value: Long) extends ExpirySet

  /** Set field expiry as Unix timestamp in milliseconds */
  case class UnixMilliseconds(value: Long) extends ExpirySet

  /** Remove the existing TTL on the field */
  case object Persist extends ExpirySet

  /** Retain the existing TTL on the field */
  case object KeepExisting extends ExpirySet
}

/** Conditional mode for HSETEX field operations */
sealed trait FieldCondition

object FieldCondition {

  /** Only set fields if all specified fields already exist */
  case object OnlyIfAllExist extends FieldCondition

  /** Only set fields if none of the specified fields exist */
  case object OnlyIfNoneExist extends FieldCondition
}
