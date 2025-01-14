---
layout: docs
title:  "Codecs"
number: 3
position: 3
---

# Codecs

Valkey is a key-value store, and as such, it is commonly used to store simple values in a "stringy" form. Valkey4Cats parameterizes the type of keys and values using a `Codec[A]` type class that handles encoding/decoding to and from Glide's internal `GlideString` format.

## Built-in Codecs

Valkey4Cats provides the following codecs out of the box:

| Type | Description |
|------|-------------|
| `Codec[String]` | UTF-8 encoded strings (most common) |
| `Codec[Array[Byte]]` | Raw byte array pass-through |
| `Codec[Long]` | Long integers (stored as string representation) |
| `Codec[Int]` | Integers (stored as string representation) |
| `Codec[Double]` | Double-precision floats (stored as string representation) |

## Using codecs

The most common usage is with `String` keys and values, which is what `utf8` provides:

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]

// Uses Codec[String] for both keys and values
val utf8Api = Valkey[IO].utf8("valkey://localhost:6379")
```

For other types, use `simple` which resolves codecs implicitly:

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]

// String keys, Long values
val longApi = Valkey[IO].simple[String, Long]("valkey://localhost:6379")

// String keys, byte array values
val bytesApi = Valkey[IO].simple[String, Array[Byte]]("valkey://localhost:6379")
```

## Custom codecs

You can create a `Codec` for any type by implementing the `encode` and `decode` methods:

```scala mdoc:compile-only
import dev.profunktor.valkey4cats.codec.Codec
import glide.api.models.GlideString
import java.nio.charset.StandardCharsets

// A codec for a custom ID type
case class UserId(value: String)

given Codec[UserId] = new Codec[UserId]:
  def encode(value: UserId): GlideString =
    GlideString.of(value.value.getBytes(StandardCharsets.UTF_8))
  def decode(gs: GlideString): UserId =
    UserId(new String(gs.getBytes(), StandardCharsets.UTF_8))
```

## JSON codecs

For JSON serialization, you can derive a codec using your preferred JSON library. For example, with Circe:

```scala
import dev.profunktor.valkey4cats.codec.Codec
import glide.api.models.GlideString
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.decode as jsonDecode
import java.nio.charset.StandardCharsets

case class User(name: String, age: Int) derives Encoder.AsObject, Decoder

given Codec[User] = new Codec[User]:
  def encode(value: User): GlideString =
    GlideString.of(value.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
  def decode(gs: GlideString): User =
    val str = new String(gs.getBytes(), StandardCharsets.UTF_8)
    jsonDecode[User](str).getOrElse(
      throw new RuntimeException(s"Failed to decode User from: $str")
    )
```

Although it is possible to derive a JSON codec, it is mainly preferred to use a simple codec like `Codec[String]` and manage the encoding/decoding yourself (separation of concerns). This way, you can use a single active Valkey connection for more than one type of message.

## Deriving codecs from existing ones

You can transform an existing codec using `map` and `contramap` patterns:

```scala mdoc:compile-only
import dev.profunktor.valkey4cats.codec.Codec
import glide.api.models.GlideString

// Derive a new codec by transforming an existing one
def deriveCodec[A, B](base: Codec[A])(to: A => B)(from: B => A): Codec[B] =
  new Codec[B]:
    def encode(value: B): GlideString = base.encode(from(value))
    def decode(gs: GlideString): B = to(base.decode(gs))

// Example: newtype wrapper
case class Email(value: String)

given Codec[Email] = deriveCodec(Codec[String])(Email.apply)(_.value)
```
