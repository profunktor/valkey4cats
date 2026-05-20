package dev.profunktor.valkey4cats.arguments

/** Direction for list operations (LMOVE, BLMOVE, etc.) */
sealed trait ListDirection

object ListDirection {

  /** Operate on the head (left end) of the list */
  case object Left extends ListDirection

  /** Operate on the tail (right end) of the list */
  case object Right extends ListDirection
}
