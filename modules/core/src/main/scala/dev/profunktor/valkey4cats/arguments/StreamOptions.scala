package dev.profunktor.valkey4cats.arguments

/** Trimming strategy for stream commands (XADD, XTRIM) */
sealed trait StreamTrimStrategy

object StreamTrimStrategy {

  /** Trim stream to at most the given number of entries */
  final case class MaxLen(threshold: Long, exact: Boolean = true)
      extends StreamTrimStrategy

  /** Trim entries with IDs lower than the given minimum ID */
  final case class MinId(id: String, exact: Boolean = true)
      extends StreamTrimStrategy
}

/** Bound for stream range queries (XRANGE, XREVRANGE) */
sealed trait StreamRangeBound

object StreamRangeBound {

  /** The minimum possible stream ID (start of stream) */
  case object Min extends StreamRangeBound

  /** The maximum possible stream ID (end of stream) */
  case object Max extends StreamRangeBound

  /** An inclusive stream ID bound */
  final case class Id(id: String) extends StreamRangeBound

  /** An exclusive stream ID bound */
  final case class ExclusiveId(id: String) extends StreamRangeBound
}
