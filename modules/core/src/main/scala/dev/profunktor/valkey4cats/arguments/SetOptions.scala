package dev.profunktor.valkey4cats.arguments

/** Expiry options for SET command */
sealed trait SetExpiry

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
