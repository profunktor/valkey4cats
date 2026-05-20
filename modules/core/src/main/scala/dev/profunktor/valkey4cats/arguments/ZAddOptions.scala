package dev.profunktor.valkey4cats.arguments

/** Conditional change mode for ZADD command */
sealed trait ZAddConditionalChange

object ZAddConditionalChange {

  /** Only add new elements. Don't update existing elements (NX) */
  case object OnlyIfNotExists extends ZAddConditionalChange

  /** Only update existing elements. Don't add new elements (XX) */
  case object OnlyIfExists extends ZAddConditionalChange
}

/** Update options for ZADD command */
sealed trait ZAddUpdateOption

object ZAddUpdateOption {

  /** Only update if new score is greater than current score (GT) */
  case object GreaterThan extends ZAddUpdateOption

  /** Only update if new score is less than current score (LT) */
  case object LessThan extends ZAddUpdateOption
}

/** Options for ZADD command
  *
  * Example:
  * {{{
  * import dev.profunktor.valkey4cats.arguments._
  *
  * // Add only if not exists
  * ZAddOptions(conditionalChange = Some(ZAddConditionalChange.OnlyIfNotExists))
  *
  * // Update only if new score is greater
  * ZAddOptions(updateOption = Some(ZAddUpdateOption.GreaterThan))
  *
  * // Combine options
  * ZAddOptions(
  *   conditionalChange = Some(ZAddConditionalChange.OnlyIfExists),
  *   updateOption = Some(ZAddUpdateOption.GreaterThan)
  * )
  * }}}
  */
final case class ZAddOptions(
    conditionalChange: Option[ZAddConditionalChange] = None,
    updateOption: Option[ZAddUpdateOption] = None
)
