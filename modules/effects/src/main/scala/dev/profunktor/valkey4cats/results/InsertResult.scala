package dev.profunktor.valkey4cats.results

/** Result of a LINSERT operation, indicating whether the element was inserted or the pivot was not found. */
sealed trait InsertResult

object InsertResult {

  /** The element was inserted; contains the new list length. */
  case class Inserted(newLength: Long) extends InsertResult

  /** The pivot element was not found in the list. */
  case object PivotNotFound extends InsertResult
}
