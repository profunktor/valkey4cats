package dev.profunktor.valkey4cats.codec

import java.nio.charset.StandardCharsets

trait Encoder[A]:
  def encode(value: A): Array[Byte]

trait ByteDecoder[A]:
  def decode(bytes: Array[Byte]): A

trait Codec[A] extends Encoder[A] with ByteDecoder[A]

object Codec {

  def apply[A](implicit codec: Codec[A]): Codec[A] = codec

  implicit val utf8Codec: Codec[String] = new Codec[String] {
    def encode(value: String): Array[Byte] =
      value.getBytes(StandardCharsets.UTF_8)

    def decode(bytes: Array[Byte]): String =
      new String(bytes, StandardCharsets.UTF_8)
  }

  implicit val byteArrayCodec: Codec[Array[Byte]] =
    new Codec[Array[Byte]] {
      def encode(value: Array[Byte]): Array[Byte] = value
      def decode(bytes: Array[Byte]): Array[Byte] = bytes
    }

  implicit val longCodec: Codec[Long] = new Codec[Long] {
    def encode(value: Long): Array[Byte] =
      utf8Codec.encode(value.toString)

    def decode(bytes: Array[Byte]): Long =
      utf8Codec.decode(bytes).toLong
  }

  implicit val intCodec: Codec[Int] = new Codec[Int] {
    def encode(value: Int): Array[Byte] =
      utf8Codec.encode(value.toString)

    def decode(bytes: Array[Byte]): Int =
      utf8Codec.decode(bytes).toInt
  }

  implicit val doubleCodec: Codec[Double] = new Codec[Double] {
    def encode(value: Double): Array[Byte] =
      utf8Codec.encode(value.toString)

    def decode(bytes: Array[Byte]): Double =
      utf8Codec.decode(bytes).toDouble
  }
}
