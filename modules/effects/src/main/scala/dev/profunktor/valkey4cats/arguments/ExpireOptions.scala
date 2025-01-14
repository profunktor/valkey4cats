package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.ExpireOptions
import dev.profunktor.valkey4cats.arguments.ExpireCondition.{
  OnlyIfGreater,
  OnlyIfHasExpiry,
  OnlyIfLess,
  OnlyIfNoExpiry
}

/** Conditional mode for EXPIRE command */
sealed trait ExpireCondition { self =>

  private[valkey4cats] def toGlide: ExpireOptions =
    self match {
      case OnlyIfNoExpiry  => ExpireOptions.HAS_NO_EXPIRY
      case OnlyIfHasExpiry => ExpireOptions.HAS_EXISTING_EXPIRY
      case OnlyIfGreater   => ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT
      case OnlyIfLess      => ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT
    }
}

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
