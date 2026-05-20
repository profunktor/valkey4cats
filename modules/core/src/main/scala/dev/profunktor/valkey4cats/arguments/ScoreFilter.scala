package dev.profunktor.valkey4cats.arguments

/** Filter for selecting the minimum or maximum scoring element */
sealed trait ScoreFilter

object ScoreFilter {

  /** Select the element with the lowest score */
  case object Min extends ScoreFilter

  /** Select the element with the highest score */
  case object Max extends ScoreFilter
}
