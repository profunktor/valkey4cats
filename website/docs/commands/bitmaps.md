---
sidebar_position: 10
title: Bitmaps
---

# Bitmaps API

Purely functional interface for the [Bitmaps API](https://valkey.io/commands/#bitmap).

Bitmaps are not an actual data type, but a set of bit-oriented operations defined on the String type.

## Usage

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.arguments.BitwiseOperation

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // SETBIT
    prev <- valkey.setbit("mybitmap", 7, 1)
    _ <- IO.println(s"Previous bit at 7: ${prev.toOption}") // Some(0)
    _ <- valkey.setbit("mybitmap", 3, 1)

    // GETBIT
    bit <- valkey.getbit("mybitmap", 7)
    _ <- IO.println(s"Bit at 7: ${bit.toOption}") // Some(1)

    // BITCOUNT
    count <- valkey.bitcount("mybitmap")
    _ <- IO.println(s"Total set bits: ${count.toOption}") // Some(2)

    // BITPOS - find first bit set to 1
    pos <- valkey.bitpos("mybitmap", 1)
    _ <- IO.println(s"First 1-bit position: ${pos.toOption}") // Some(3)

    // BITOP
    _ <- valkey.setbit("bitmap_a", 0, 1)
    _ <- valkey.setbit("bitmap_b", 1, 1)
    _ <- valkey.bitop(BitwiseOperation.Or, "result_or", "bitmap_a", "bitmap_b")
  yield ()
}
```

## Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| SETBIT | `setbit(key, offset, value)` | `F[ValkeyResponse[Long]]` |
| GETBIT | `getbit(key, offset)` | `F[ValkeyResponse[Long]]` |
| BITCOUNT | `bitcount(key)` | `F[ValkeyResponse[Long]]` |
| BITCOUNT (range) | `bitcount(key, start, end)` | `F[ValkeyResponse[Long]]` |
| BITPOS | `bitpos(key, bit)` | `F[ValkeyResponse[Long]]` |
| BITPOS (range) | `bitpos(key, bit, start, end)` | `F[ValkeyResponse[Long]]` |
| BITOP | `bitop(operation, destkey, keys*)` | `F[ValkeyResponse[Long]]` |
