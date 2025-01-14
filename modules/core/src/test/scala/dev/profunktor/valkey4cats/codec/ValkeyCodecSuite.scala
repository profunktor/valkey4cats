package dev.profunktor.valkey4cats.codec

import munit.FunSuite

class ValkeyCodecSuite extends FunSuite {

  test("StringCodec should encode and decode strings correctly") {
    val codec = Codec.utf8Codec
    val original = "Hello, Valkey!"

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)

    assertEquals(decoded, original)
  }

  test("StringCodec should handle empty strings") {
    val codec = Codec.utf8Codec
    val original = ""

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)

    assertEquals(decoded, original)
  }

  test("StringCodec should handle UTF-8 characters") {
    val codec = Codec.utf8Codec
    val original = "Hello 世界 🌍"

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)

    assertEquals(decoded, original)
  }

  test("ByteArrayCodec should encode and decode bytes correctly") {
    val codec = Codec.byteArrayCodec
    val original = Array[Byte](1, 2, 3, 4, 5)

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)

    assert(decoded.sameElements(original))
  }

  test("ByteArrayCodec should handle empty arrays") {
    val codec = Codec.byteArrayCodec
    val original = Array.empty[Byte]

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)

    assert(decoded.sameElements(original))
  }

  test("LongCodec should encode and decode longs correctly") {
    val codec = Codec.longCodec
    val original = 42L

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)

    assertEquals(decoded, original)
  }

  test("LongCodec should handle negative numbers") {
    val codec = Codec.longCodec
    val original = -12345L

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)

    assertEquals(decoded, original)
  }

  test("IntCodec should encode and decode ints correctly") {
    val codec = Codec.intCodec
    val original = 100

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)

    assertEquals(decoded, original)
  }

  test("DoubleCodec should encode and decode doubles correctly") {
    val codec = Codec.doubleCodec
    val original = 3.14159

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)

    assertEquals(decoded, original)
  }

  test("DoubleCodec should handle scientific notation") {
    val codec = Codec.doubleCodec
    val original = 1.23e10

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)

    assertEquals(decoded, original)
  }
}
