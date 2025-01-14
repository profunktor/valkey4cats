package dev.profunktor.valkey4cats.results

/** Result of a SET operation with GET semantics, indicating whether the key was written, replaced, or not set. */
sealed trait SetResult[+V]

object SetResult {

  /** The key was newly written (no previous value existed). */
  case object Written extends SetResult[Nothing]

  /** The key was overwritten; contains the previous value. */
  case class Replaced[V](oldValue: V) extends SetResult[V]

  /** The key was not set (e.g. NX/XX condition not met). */
  case object NotSet extends SetResult[Nothing]
}
