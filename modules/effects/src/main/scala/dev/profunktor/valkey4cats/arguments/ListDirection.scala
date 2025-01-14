package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.{ListDirection => GlideListDirection}

/** Direction for list operations (LMOVE, BLMOVE, etc.) */
sealed trait ListDirection {
  def toGlide: GlideListDirection = this match {
    case ListDirection.Left  => GlideListDirection.LEFT
    case ListDirection.Right => GlideListDirection.RIGHT
  }
}

object ListDirection {

  /** Operate on the head (left end) of the list */
  case object Left extends ListDirection

  /** Operate on the tail (right end) of the list */
  case object Right extends ListDirection
}
