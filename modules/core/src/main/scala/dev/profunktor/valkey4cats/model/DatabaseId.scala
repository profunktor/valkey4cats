package dev.profunktor.valkey4cats.model

/** Validated Valkey database index, restricted to the range 0-15. */
opaque type DatabaseId = Int

object DatabaseId {

  val MinValue: Int = 0
  val MaxValue: Int = 15

  def apply(value: Int): Either[String, DatabaseId] =
    if (value >= MinValue && value <= MaxValue) Right(value)
    else
      Left(s"Database ID must be between $MinValue and $MaxValue, got: $value")

  def unsafe(value: Int): DatabaseId = apply(value) match {
    case Right(id) => id
    case Left(msg) => throw new IllegalArgumentException(msg)
  }

  extension (id: DatabaseId) {
    def value: Int = id
  }
}
