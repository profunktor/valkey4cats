package dev.profunktor.valkey4cats.arguments

/** Aggregation function for sorted set combination commands (ZUNIONSTORE, ZINTERSTORE) */
sealed trait AggregateOption

object AggregateOption {

  /** Sum the scores of matching elements across inputs */
  case object Sum extends AggregateOption

  /** Take the minimum score among matching elements */
  case object Min extends AggregateOption

  /** Take the maximum score among matching elements */
  case object Max extends AggregateOption
}
