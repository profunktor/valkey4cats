package dev.profunktor.valkey4cats.arguments

/** Flush mode options for database flush operations */
sealed trait FlushMode

object FlushMode {

  /** Perform flush synchronously (blocks until complete) */
  case object Sync extends FlushMode

  /** Perform flush asynchronously (non-blocking) */
  case object Async extends FlushMode
}
