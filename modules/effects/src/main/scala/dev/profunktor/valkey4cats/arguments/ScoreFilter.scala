package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.{ScoreFilter => GlideScoreFilter}

/** Filter for selecting the minimum or maximum scoring element */
sealed trait ScoreFilter {
  private[valkey4cats] def toGlide: GlideScoreFilter
}

object ScoreFilter {

  /** Select the element with the lowest score */
  case object Min extends ScoreFilter {
    def toGlide: GlideScoreFilter = GlideScoreFilter.MIN
  }

  /** Select the element with the highest score */
  case object Max extends ScoreFilter {
    def toGlide: GlideScoreFilter = GlideScoreFilter.MAX
  }
}
