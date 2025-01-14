package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.stream.{
  StreamRange => GlideStreamRange,
  StreamTrimOptions => GlideStreamTrimOptions
}

/** Trimming strategy for stream commands (XADD, XTRIM) */
sealed trait StreamTrimStrategy {
  def toGlide: GlideStreamTrimOptions
}

object StreamTrimStrategy {

  /** Trim stream to at most the given number of entries */
  final case class MaxLen(threshold: Long, exact: Boolean = true)
      extends StreamTrimStrategy {
    def toGlide: GlideStreamTrimOptions =
      new GlideStreamTrimOptions.MaxLen(exact, threshold)
  }

  /** Trim entries with IDs lower than the given minimum ID */
  final case class MinId(id: String, exact: Boolean = true)
      extends StreamTrimStrategy {
    def toGlide: GlideStreamTrimOptions =
      new GlideStreamTrimOptions.MinId(exact, id)
  }
}

/** Bound for stream range queries (XRANGE, XREVRANGE) */
sealed trait StreamRangeBound {
  def toGlide: GlideStreamRange
}

object StreamRangeBound {

  /** The minimum possible stream ID (start of stream) */
  case object Min extends StreamRangeBound {
    def toGlide: GlideStreamRange = GlideStreamRange.InfRangeBound.MIN
  }

  /** The maximum possible stream ID (end of stream) */
  case object Max extends StreamRangeBound {
    def toGlide: GlideStreamRange = GlideStreamRange.InfRangeBound.MAX
  }

  /** An inclusive stream ID bound */
  final case class Id(id: String) extends StreamRangeBound {
    def toGlide: GlideStreamRange = GlideStreamRange.IdBound.of(id)
  }

  /** An exclusive stream ID bound */
  final case class ExclusiveId(id: String) extends StreamRangeBound {
    def toGlide: GlideStreamRange =
      GlideStreamRange.IdBound.ofExclusive(id)
  }
}
