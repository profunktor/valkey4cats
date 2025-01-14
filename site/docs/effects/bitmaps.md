---
layout: docs
title:  "Bitmaps"
number: 13
---

# Bitmaps API

Purely functional interface for the [Bitmaps API](https://valkey.io/commands/#bitmap).

Bitmaps are not an actual data type, but a set of bit-oriented operations defined on the String type. They allow you to manipulate individual bits within a string value.

### Bitmap Commands usage

Once you have acquired a connection you can start using it:

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}
import dev.profunktor.valkey4cats.arguments.{BitmapIndexType, BitwiseOperation}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // SETBIT - set bit at offset
    prev <- valkey.setbit("mybitmap", 7, 1)
    _ <- IO.println(s"Previous bit at 7: ${prev.toOption}") // Some(0)

    _ <- valkey.setbit("mybitmap", 3, 1)

    // GETBIT - get bit at offset
    bit <- valkey.getbit("mybitmap", 7)
    _ <- IO.println(s"Bit at 7: ${bit.toOption}") // Some(1)

    bit0 <- valkey.getbit("mybitmap", 0)
    _ <- IO.println(s"Bit at 0: ${bit0.toOption}") // Some(0)

    // BITCOUNT - count set bits
    count <- valkey.bitcount("mybitmap")
    _ <- IO.println(s"Total set bits: ${count.toOption}") // Some(2)

    // BITCOUNT with range
    rangeCount <- valkey.bitcount("mybitmap", 0, 0)
    _ <- IO.println(s"Set bits in first byte: ${rangeCount.toOption}")

    // BITPOS - find first bit set to 1
    pos <- valkey.bitpos("mybitmap", 1)
    _ <- IO.println(s"First 1-bit position: ${pos.toOption}") // Some(3)

    // BITOP - bitwise operations between keys
    _ <- valkey.setbit("bitmap_a", 0, 1)
    _ <- valkey.setbit("bitmap_a", 2, 1)
    _ <- valkey.setbit("bitmap_b", 1, 1)
    _ <- valkey.setbit("bitmap_b", 2, 1)

    _ <- valkey.bitop(BitwiseOperation.And, "result_and", "bitmap_a", "bitmap_b")
    _ <- valkey.bitop(BitwiseOperation.Or, "result_or", "bitmap_a", "bitmap_b")
    _ <- valkey.bitop(BitwiseOperation.Xor, "result_xor", "bitmap_a", "bitmap_b")
    _ <- valkey.bitop(BitwiseOperation.Not, "result_not", "bitmap_a")

    // Verify OR result
    orBit0 <- valkey.getbit("result_or", 0)
    orBit1 <- valkey.getbit("result_or", 1)
    orBit2 <- valkey.getbit("result_or", 2)
    _ <- IO.println(s"OR bits [0,1,2]: ${List(orBit0, orBit1, orBit2).map(_.toOption)}")
    // [Some(1), Some(1), Some(1)]
  yield ()
}
```

### Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| SETBIT | `setbit(key, offset, value)` | `F[ValkeyResponse[Long]]` |
| GETBIT | `getbit(key, offset)` | `F[ValkeyResponse[Long]]` |
| BITCOUNT | `bitcount(key)` | `F[ValkeyResponse[Long]]` |
| BITCOUNT (range) | `bitcount(key, start, end)` | `F[ValkeyResponse[Long]]` |
| BITCOUNT (index type) | `bitcount(key, start, end, indexType)` | `F[ValkeyResponse[Long]]` |
| BITPOS | `bitpos(key, bit)` | `F[ValkeyResponse[Long]]` |
| BITPOS (start) | `bitpos(key, bit, start)` | `F[ValkeyResponse[Long]]` |
| BITPOS (range) | `bitpos(key, bit, start, end)` | `F[ValkeyResponse[Long]]` |
| BITPOS (index type) | `bitpos(key, bit, start, end, indexType)` | `F[ValkeyResponse[Long]]` |
| BITOP | `bitop(operation, destkey, keys*)` | `F[ValkeyResponse[Long]]` |
