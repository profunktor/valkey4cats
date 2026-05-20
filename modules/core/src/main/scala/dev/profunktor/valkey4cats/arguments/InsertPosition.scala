package dev.profunktor.valkey4cats.arguments

/** Position for LINSERT command */
sealed trait InsertPosition

object InsertPosition {

  /** Insert before the pivot element */
  case object Before extends InsertPosition

  /** Insert after the pivot element */
  case object After extends InsertPosition
}
