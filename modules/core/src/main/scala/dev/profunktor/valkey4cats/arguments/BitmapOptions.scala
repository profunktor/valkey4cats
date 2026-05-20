package dev.profunktor.valkey4cats.arguments

/** Index type for BITCOUNT and BITPOS range arguments */
sealed trait BitmapIndexType

object BitmapIndexType {

  /** Interpret range offsets as byte positions */
  case object Byte extends BitmapIndexType

  /** Interpret range offsets as bit positions */
  case object Bit extends BitmapIndexType
}

/** Bitwise operation for the BITOP command */
sealed trait BitwiseOperation

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
