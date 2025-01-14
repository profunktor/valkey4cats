package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.LInsertOptions

/** Position for LINSERT command */
sealed trait InsertPosition { self =>
  private[valkey4cats] def toGlide: LInsertOptions.InsertPosition =
    self match {
      case InsertPosition.Before => LInsertOptions.InsertPosition.BEFORE
      case InsertPosition.After  => LInsertOptions.InsertPosition.AFTER
    }
}

object InsertPosition {

  /** Insert before the pivot element */
  case object Before extends InsertPosition

  /** Insert after the pivot element */
  case object After extends InsertPosition
}
