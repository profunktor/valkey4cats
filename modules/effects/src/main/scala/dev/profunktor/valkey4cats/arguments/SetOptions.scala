package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands as G
import dev.profunktor.valkey4cats.arguments.SetExpiry.{
  KeepExisting,
  Milliseconds,
  Seconds,
  UnixMilliseconds,
  UnixSeconds
}

/** Expiry options for SET command */
sealed trait SetExpiry { self =>
  private[valkey4cats] def toGlide: G.SetOptions.Expiry =
    self match {
      case KeepExisting         => G.SetOptions.Expiry.KeepExisting()
      case Seconds(s)           => G.SetOptions.Expiry.Seconds(s)
      case Milliseconds(ms)     => G.SetOptions.Expiry.Milliseconds(ms)
      case UnixSeconds(ts)      => G.SetOptions.Expiry.UnixSeconds(ts)
      case UnixMilliseconds(ts) => G.SetOptions.Expiry.UnixMilliseconds(ts)
    }
}

object SetExpiry {

  /** Keep the existing TTL */
  case object KeepExisting extends SetExpiry

  /** Set expiry in seconds */
  final case class Seconds(seconds: Long) extends SetExpiry

  /** Set expiry in milliseconds */
  final case class Milliseconds(milliseconds: Long) extends SetExpiry

  /** Set expiry as Unix timestamp in seconds */
  final case class UnixSeconds(timestamp: Long) extends SetExpiry

  /** Set expiry as Unix timestamp in milliseconds */
  final case class UnixMilliseconds(timestamp: Long) extends SetExpiry

}

/** Conditional set options for SET command */
sealed trait SetCondition

object SetCondition {

  /** Only set the key if it does not already exist (NX) */
  case object OnlyIfNotExists extends SetCondition

  /** Only set the key if it already exists (XX) */
  case object OnlyIfExists extends SetCondition

  /** Only set if the current value equals the given value */
  final case class OnlyIfEqualTo(value: String) extends SetCondition

  private[valkey4cats] def applyToBuilder(
      condition: SetCondition,
      builder: G.SetOptions.SetOptionsBuilder
  ): G.SetOptions.SetOptionsBuilder =
    condition match {
      case OnlyIfNotExists  => builder.conditionalSetOnlyIfNotExist()
      case OnlyIfExists     => builder.conditionalSetOnlyIfExists()
      case OnlyIfEqualTo(v) => builder.conditionalSetOnlyIfEqualTo(v)
    }
}

/** Options for SET command
  *
  * Example:
  * {{{
  * import dev.profunktor.valkey4cats.arguments._
  *
  * // Set with 60 second expiry
  * SetOptions(expiry = Some(SetExpiry.Seconds(60)))
  *
  * // Set only if not exists with expiry
  * SetOptions(
  *   condition = Some(SetCondition.OnlyIfNotExists),
  *   expiry = Some(SetExpiry.Seconds(60))
  * )
  *
  * // Set and return old value
  * SetOptions(returnOldValue = true)
  * }}}
  */
final case class SetOptions(
    expiry: Option[SetExpiry] = None,
    condition: Option[SetCondition] = None,
    returnOldValue: Boolean = false
)

object SetOptions {
  private[valkey4cats] def toGlide(options: SetOptions): G.SetOptions = {
    val b0 = G.SetOptions.builder()
    val b1 = options.expiry.fold(b0)(e => b0.expiry(e.toGlide))
    val b2 = options.condition.fold(b1)(c => SetCondition.applyToBuilder(c, b1))
    val b3 = if (options.returnOldValue) b2.returnOldValue(true) else b2
    b3.build()
  }
}
