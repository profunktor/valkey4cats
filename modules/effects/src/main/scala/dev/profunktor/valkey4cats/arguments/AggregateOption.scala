package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.WeightAggregateOptions.{
  Aggregate => GlideAggregate
}

/** Aggregation function for sorted set combination commands (ZUNIONSTORE, ZINTERSTORE) */
sealed trait AggregateOption {
  private[valkey4cats] def toGlide: GlideAggregate
}

object AggregateOption {

  /** Sum the scores of matching elements across inputs */
  case object Sum extends AggregateOption {
    def toGlide: GlideAggregate = GlideAggregate.SUM
  }

  /** Take the minimum score among matching elements */
  case object Min extends AggregateOption {
    def toGlide: GlideAggregate = GlideAggregate.MIN
  }

  /** Take the maximum score among matching elements */
  case object Max extends AggregateOption {
    def toGlide: GlideAggregate = GlideAggregate.MAX
  }
}
