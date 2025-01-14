package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.RangeOptions as G
import dev.profunktor.valkey4cats.arguments.LexBoundary.Lex
import dev.profunktor.valkey4cats.arguments.RangeQuery.{ByIndex, ByLex, ByScore}
import dev.profunktor.valkey4cats.arguments.ScoreBoundary.Score

/** Range query for sorted set commands */
sealed trait RangeQuery { self =>
  private[valkey4cats] def toGlide: G.RangeQuery =
    self match {
      case ByIndex(start, stop) =>
        new G.RangeByIndex(start, stop)
      case ByScore(min, max) =>
        new G.RangeByScore(min.toGlide, max.toGlide)
      case ByLex(min, max) =>
        new G.RangeByLex(min.toGlide, max.toGlide)
    }

}

object RangeQuery {

  /** Range by index (rank)
    *
    * @param start Start index (inclusive, 0-based, can be negative)
    * @param stop Stop index (inclusive, can be negative)
    */
  final case class ByIndex(start: Long, stop: Long) extends RangeQuery

  /** Range by score
    *
    * @param min Minimum score boundary
    * @param max Maximum score boundary
    */
  final case class ByScore(min: ScoreBoundary, max: ScoreBoundary)
      extends RangeQuery

  /** Range by lexicographic order
    *
    * @param min Minimum lex boundary
    * @param max Maximum lex boundary
    */
  final case class ByLex(min: LexBoundary, max: LexBoundary) extends RangeQuery

}

/** Score boundary for range queries */
sealed trait ScoreBoundary { self =>

  private[valkey4cats] def toGlide: G.ScoreRange =
    self match {
      case ScoreBoundary.PositiveInfinity => G.InfScoreBound.POSITIVE_INFINITY
      case ScoreBoundary.NegativeInfinity => G.InfScoreBound.NEGATIVE_INFINITY
      case Score(s, i)                    => new G.ScoreBoundary(s, i)
    }
}

object ScoreBoundary {

  /** Positive infinity */
  case object PositiveInfinity extends ScoreBoundary

  /** Negative infinity */
  case object NegativeInfinity extends ScoreBoundary

  /** Specific score value
    *
    * @param score The score value
    * @param inclusive Whether the boundary is inclusive (default: true)
    */
  final case class Score(score: Double, inclusive: Boolean = true)
      extends ScoreBoundary

}

/** Lexicographic boundary for range queries */
sealed trait LexBoundary { self =>
  private[valkey4cats] def toGlide: G.LexRange =
    self match {
      case LexBoundary.PositiveInfinity => G.InfLexBound.POSITIVE_INFINITY
      case LexBoundary.NegativeInfinity => G.InfLexBound.NEGATIVE_INFINITY
      case Lex(v, i)                    => new G.LexBoundary(v, i)
    }
}

object LexBoundary {

  /** Positive infinity */
  case object PositiveInfinity extends LexBoundary

  /** Negative infinity */
  case object NegativeInfinity extends LexBoundary

  /** Specific lexicographic value
    *
    * @param value The string value
    * @param inclusive Whether the boundary is inclusive (default: true)
    */
  final case class Lex(value: String, inclusive: Boolean = true)
      extends LexBoundary

}

/** Limit for range queries */
final case class RangeLimit(offset: Long, count: Long)

object RangeLimit {
  private[valkey4cats] def toGlide(limit: RangeLimit): G.Limit =
    new G.Limit(limit.offset, limit.count)
}

/** Options for ZRANGE and related commands */
final case class ZRangeOptions(
    reverse: Boolean = false,
    limit: Option[RangeLimit] = None
)
