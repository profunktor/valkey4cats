package dev.profunktor.valkey4cats.codec

import glide.api.models.GlideString

import java.nio.charset.StandardCharsets

/** Type class for encoding/decoding values to/from Glide's GlideString format
  *
  * @tparam A The type to encode/decode
  */
trait Codec[A] {
  def encode(value: A): GlideString
  def decode(gs: GlideString): A
}

object Codec {

  def apply[A](implicit codec: Codec[A]): Codec[A] = codec

  /** String codec using UTF-8 encoding */
  implicit val utf8Codec: Codec[String] = new Codec[String] {
    def encode(value: String): GlideString =
      GlideString.of(value.getBytes(StandardCharsets.UTF_8))

    def decode(gs: GlideString): String =
      new String(gs.getBytes(), StandardCharsets.UTF_8)
  }

  /** Byte array codec (pass-through) */
  implicit val byteArrayCodec: Codec[Array[Byte]] =
    new Codec[Array[Byte]] {
      def encode(value: Array[Byte]): GlideString =
        GlideString.of(value)

      def decode(gs: GlideString): Array[Byte] =
        gs.getBytes()
    }

  /** Long codec (stores as string representation) */
  implicit val longCodec: Codec[Long] = new Codec[Long] {
    def encode(value: Long): GlideString =
      utf8Codec.encode(value.toString)

    def decode(gs: GlideString): Long =
      utf8Codec.decode(gs).toLong
  }

  /** Int codec (stores as string representation) */
  implicit val intCodec: Codec[Int] = new Codec[Int] {
    def encode(value: Int): GlideString =
      utf8Codec.encode(value.toString)

    def decode(gs: GlideString): Int =
      utf8Codec.decode(gs).toInt
  }

  /** Double codec (stores as string representation) */
  implicit val doubleCodec: Codec[Double] = new Codec[Double] {
    def encode(value: Double): GlideString =
      utf8Codec.encode(value.toString)

    def decode(gs: GlideString): Double =
      utf8Codec.decode(gs).toDouble
  }
}
