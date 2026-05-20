package dev.profunktor.valkey4cats.arguments

/** Range query for sorted set commands */
sealed trait RangeQuery

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
sealed trait ScoreBoundary

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
sealed trait LexBoundary

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

/** Options for ZRANGE and related commands */
final case class ZRangeOptions(
    reverse: Boolean = false,
    limit: Option[RangeLimit] = None
)
