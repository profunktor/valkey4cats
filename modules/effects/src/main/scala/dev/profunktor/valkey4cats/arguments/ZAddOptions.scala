package dev.profunktor.valkey4cats.arguments

import glide.api.models.{commands => G}

/** Conditional change mode for ZADD command */
sealed trait ZAddConditionalChange { self =>
  private[valkey4cats] def toGlide: G.ZAddOptions.ConditionalChange =
    self match {
      case ZAddConditionalChange.OnlyIfNotExists =>
        G.ZAddOptions.ConditionalChange.ONLY_IF_DOES_NOT_EXIST
      case ZAddConditionalChange.OnlyIfExists =>
        G.ZAddOptions.ConditionalChange.ONLY_IF_EXISTS
    }
}

object ZAddConditionalChange {

  /** Only add new elements. Don't update existing elements (NX) */
  case object OnlyIfNotExists extends ZAddConditionalChange

  /** Only update existing elements. Don't add new elements (XX) */
  case object OnlyIfExists extends ZAddConditionalChange
}

/** Update options for ZADD command */
sealed trait ZAddUpdateOption { self =>
  private[valkey4cats] def toGlide: G.ZAddOptions.UpdateOptions =
    self match {
      case ZAddUpdateOption.GreaterThan =>
        G.ZAddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT
      case ZAddUpdateOption.LessThan =>
        G.ZAddOptions.UpdateOptions.SCORE_LESS_THAN_CURRENT
    }
}

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

object ZAddOptions {
  private[valkey4cats] def toGlide(options: ZAddOptions): G.ZAddOptions = {
    val builder = G.ZAddOptions.builder()

    options.conditionalChange.foreach(cc =>
      builder.conditionalChange(cc.toGlide)
    )
    options.updateOption.foreach(uo => builder.updateOptions(uo.toGlide))

    builder.build()
  }
}
