package dev.profunktor.valkey4cats.arguments

/** Conditional mode for EXPIRE command */
sealed trait ExpireCondition

object ExpireCondition {

  /** Set expiry only when the key has no expiry (NX) */
  case object OnlyIfNoExpiry extends ExpireCondition

  /** Set expiry only when the key has an existing expiry (XX) */
  case object OnlyIfHasExpiry extends ExpireCondition

  /** Set expiry only when the new expiry is greater than current (GT) */
  case object OnlyIfGreater extends ExpireCondition

  /** Set expiry only when the new expiry is less than current (LT) */
  case object OnlyIfLess extends ExpireCondition

}
