package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.bitmap.{
  BitmapIndexType => GlideBitmapIndexType,
  BitwiseOperation => GlideBitwiseOperation
}

/** Index type for BITCOUNT and BITPOS range arguments */
sealed trait BitmapIndexType {
  def toGlide: GlideBitmapIndexType = this match {
    case BitmapIndexType.Byte => GlideBitmapIndexType.BYTE
    case BitmapIndexType.Bit  => GlideBitmapIndexType.BIT
  }
}

object BitmapIndexType {

  /** Interpret range offsets as byte positions */
  case object Byte extends BitmapIndexType

  /** Interpret range offsets as bit positions */
  case object Bit extends BitmapIndexType
}

/** Bitwise operation for the BITOP command */
sealed trait BitwiseOperation {
  def toGlide: GlideBitwiseOperation = this match {
    case BitwiseOperation.And => GlideBitwiseOperation.AND
    case BitwiseOperation.Or  => GlideBitwiseOperation.OR
    case BitwiseOperation.Xor => GlideBitwiseOperation.XOR
    case BitwiseOperation.Not => GlideBitwiseOperation.NOT
  }
}

object BitwiseOperation {

  /** Logical AND between source bitmaps */
  case object And extends BitwiseOperation

  /** Logical OR between source bitmaps */
  case object Or extends BitwiseOperation

  /** Logical XOR between source bitmaps */
  case object Xor extends BitwiseOperation

  /** Logical NOT of a single source bitmap */
  case object Not extends BitwiseOperation
}
