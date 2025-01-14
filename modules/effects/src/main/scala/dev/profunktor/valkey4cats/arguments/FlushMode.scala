package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.{FlushMode => GlideFlushMode}

/** Flush mode options for database flush operations */
sealed trait FlushMode

object FlushMode {

  /** Perform flush synchronously (blocks until complete) */
  case object Sync extends FlushMode

  /** Perform flush asynchronously (non-blocking) */
  case object Async extends FlushMode

  /** Convert to Glide FlushMode */
  def toGlide(mode: FlushMode): GlideFlushMode = mode match {
    case Sync  => GlideFlushMode.SYNC
    case Async => GlideFlushMode.ASYNC
  }
}
