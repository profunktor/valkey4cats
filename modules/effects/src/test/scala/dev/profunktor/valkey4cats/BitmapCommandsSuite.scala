package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.arguments.{BitmapIndexType, BitwiseOperation}
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

class BitmapCommandsSuite extends ValkeyTestSuite {

  // ==================== SETBIT / GETBIT ====================

  test("SETBIT should set a bit and return the original value") {
    valkeyClient.use { valkey =>
      for {
        original <- valkey.setbit("bm-set", 7, 1)
        current <- valkey.getbit("bm-set", 7)
        _ <- valkey.del("bm-set")
      } yield {
        assertEquals(original, Ok(0L))
        assertEquals(current, Ok(1L))
      }
    }
  }

  test("SETBIT should return previous bit value when overwriting") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("bm-overwrite", 7, 1)
        previous <- valkey.setbit("bm-overwrite", 7, 0)
        current <- valkey.getbit("bm-overwrite", 7)
        _ <- valkey.del("bm-overwrite")
      } yield {
        assertEquals(previous, Ok(1L))
        assertEquals(current, Ok(0L))
      }
    }
  }

  test("GETBIT should return 0 for unset bits") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.getbit("bm-unset", 100)
      } yield assertEquals(result, Ok(0L))
    }
  }

  test("GETBIT should return 0 for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.getbit("bm-nonexistent", 0)
      } yield assertEquals(result, Ok(0L))
    }
  }

  test("SETBIT should handle large offsets") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("bm-large", 1000000, 1)
        result <- valkey.getbit("bm-large", 1000000)
        zero <- valkey.getbit("bm-large", 999999)
        _ <- valkey.del("bm-large")
      } yield {
        assertEquals(result, Ok(1L))
        assertEquals(zero, Ok(0L))
      }
    }
  }

  // ==================== BITCOUNT ====================

  test("BITCOUNT should count all set bits") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("bm-count", "foobar")
        count <- valkey.bitcount("bm-count")
        _ <- valkey.del("bm-count")
      } yield assertEquals(count, Ok(26L))
    }
  }

  test("BITCOUNT should return 0 for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.bitcount("bm-count-none")
      } yield assertEquals(count, Ok(0L))
    }
  }

  test("BITCOUNT with byte range should count bits in range") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("bm-count-range", "foobar")
        count <- valkey.bitcount("bm-count-range", 0, 0)
        _ <- valkey.del("bm-count-range")
      } yield {
        val Ok(c) = count: @unchecked
        assert(
          c > 0L,
          s"First byte of 'foobar' should have some bits set, got $c"
        )
      }
    }
  }

  test("BITCOUNT with BIT index type should count bits in bit range") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("bm-count-bit", 0, 1)
        _ <- valkey.setbit("bm-count-bit", 1, 1)
        _ <- valkey.setbit("bm-count-bit", 2, 1)
        _ <- valkey.setbit("bm-count-bit", 3, 0)
        count <- valkey.bitcount("bm-count-bit", 0, 2, BitmapIndexType.Bit)
        _ <- valkey.del("bm-count-bit")
      } yield assertEquals(count, Ok(3L))
    }
  }

  // ==================== BITPOS ====================

  test("BITPOS should find position of first set bit") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("bm-pos", 10, 1)
        pos <- valkey.bitpos("bm-pos", 1)
        _ <- valkey.del("bm-pos")
      } yield assertEquals(pos, Ok(10L))
    }
  }

  test("BITPOS should find first 0 bit in non-existent key") {
    valkeyClient.use { valkey =>
      for {
        pos <- valkey.bitpos("bm-pos-nokey", 0)
      } yield assertEquals(pos, Ok(0L))
    }
  }

  test("BITPOS with start offset should search from given byte") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("bm-pos-start", 0, 1)
        _ <- valkey.setbit("bm-pos-start", 15, 1)
        pos <- valkey.bitpos("bm-pos-start", 1, 1)
        _ <- valkey.del("bm-pos-start")
      } yield assertEquals(pos, Ok(15L))
    }
  }

  test("BITPOS with start and end should search in range") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("bm-pos-range", 0, 1)
        _ <- valkey.setbit("bm-pos-range", 20, 1)
        pos <- valkey.bitpos("bm-pos-range", 1, 2, 3)
        _ <- valkey.del("bm-pos-range")
      } yield assertEquals(pos, Ok(20L))
    }
  }

  test("BITPOS with BIT index type should search by bit offsets") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("bm-pos-bittype", 5, 1)
        pos <- valkey.bitpos("bm-pos-bittype", 1, 0, 7, BitmapIndexType.Bit)
        _ <- valkey.del("bm-pos-bittype")
      } yield assertEquals(pos, Ok(5L))
    }
  }

  // ==================== BITOP ====================

  test("BITOP AND should perform bitwise AND") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("bm-and1", 0, 1)
        _ <- valkey.setbit("bm-and1", 1, 1)
        _ <- valkey.setbit("bm-and2", 0, 1)
        _ <- valkey.setbit("bm-and2", 1, 0)
        size <- valkey.bitop(
          BitwiseOperation.And,
          "bm-and-result",
          "bm-and1",
          "bm-and2"
        )
        bit0 <- valkey.getbit("bm-and-result", 0)
        bit1 <- valkey.getbit("bm-and-result", 1)
        _ <- valkey.del("bm-and1", "bm-and2", "bm-and-result")
      } yield {
        val Ok(s) = size: @unchecked
        assert(s > 0L)
        assertEquals(bit0, Ok(1L))
        assertEquals(bit1, Ok(0L))
      }
    }
  }

  test("BITOP OR should perform bitwise OR") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("bm-or1", 0, 1)
        _ <- valkey.setbit("bm-or1", 1, 0)
        _ <- valkey.setbit("bm-or2", 0, 0)
        _ <- valkey.setbit("bm-or2", 1, 1)
        _ <- valkey.bitop(
          BitwiseOperation.Or,
          "bm-or-result",
          "bm-or1",
          "bm-or2"
        )
        bit0 <- valkey.getbit("bm-or-result", 0)
        bit1 <- valkey.getbit("bm-or-result", 1)
        _ <- valkey.del("bm-or1", "bm-or2", "bm-or-result")
      } yield {
        assertEquals(bit0, Ok(1L))
        assertEquals(bit1, Ok(1L))
      }
    }
  }

  test("BITOP XOR should perform bitwise XOR") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("bm-xor1", 0, 1)
        _ <- valkey.setbit("bm-xor1", 1, 1)
        _ <- valkey.setbit("bm-xor2", 0, 1)
        _ <- valkey.setbit("bm-xor2", 1, 0)
        _ <- valkey.bitop(
          BitwiseOperation.Xor,
          "bm-xor-result",
          "bm-xor1",
          "bm-xor2"
        )
        bit0 <- valkey.getbit("bm-xor-result", 0)
        bit1 <- valkey.getbit("bm-xor-result", 1)
        _ <- valkey.del("bm-xor1", "bm-xor2", "bm-xor-result")
      } yield {
        assertEquals(bit0, Ok(0L))
        assertEquals(bit1, Ok(1L))
      }
    }
  }

  test("BITOP NOT should invert bits") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("bm-not-src", 0, 1)
        _ <- valkey.setbit("bm-not-src", 1, 0)
        _ <- valkey.bitop(BitwiseOperation.Not, "bm-not-result", "bm-not-src")
        bit0 <- valkey.getbit("bm-not-result", 0)
        bit1 <- valkey.getbit("bm-not-result", 1)
        _ <- valkey.del("bm-not-src", "bm-not-result")
      } yield {
        assertEquals(bit0, Ok(0L))
        assertEquals(bit1, Ok(1L))
      }
    }
  }

  // ==================== Complex workflows ====================

  test("bitmap workflow: user activity tracking") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.setbit("activity:day1", 100, 1)
        _ <- valkey.setbit("activity:day1", 200, 1)
        _ <- valkey.setbit("activity:day1", 300, 1)
        _ <- valkey.setbit("activity:day2", 100, 1)
        _ <- valkey.setbit("activity:day2", 400, 1)
        day1Count <- valkey.bitcount("activity:day1")
        day2Count <- valkey.bitcount("activity:day2")
        _ <- valkey.bitop(
          BitwiseOperation.And,
          "activity:both",
          "activity:day1",
          "activity:day2"
        )
        bothCount <- valkey.bitcount("activity:both")
        _ <- valkey.bitop(
          BitwiseOperation.Or,
          "activity:either",
          "activity:day1",
          "activity:day2"
        )
        eitherCount <- valkey.bitcount("activity:either")
        _ <- valkey.del(
          "activity:day1",
          "activity:day2",
          "activity:both",
          "activity:either"
        )
      } yield {
        assertEquals(day1Count, Ok(3L))
        assertEquals(day2Count, Ok(2L))
        assertEquals(bothCount, Ok(1L))
        assertEquals(eitherCount, Ok(4L))
      }
    }
  }
}
