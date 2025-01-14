package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

class HyperLogLogCommandsSuite extends ValkeyTestSuite {

  // ==================== PFADD ====================

  test("PFADD should add elements to a new HyperLogLog") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.pfadd("hll-new", "a", "b", "c")
        _ <- valkey.del("hll-new")
      } yield assertEquals(result, Ok(true))
    }
  }

  test("PFADD should return false when no new elements are added") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.pfadd("hll-dup", "a", "b", "c")
        result <- valkey.pfadd("hll-dup", "a", "b", "c")
        _ <- valkey.del("hll-dup")
      } yield assertEquals(result, Ok(false))
    }
  }

  test("PFADD should return true when at least one new element is added") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.pfadd("hll-partial", "a", "b")
        result <- valkey.pfadd("hll-partial", "b", "c")
        _ <- valkey.del("hll-partial")
      } yield assertEquals(result, Ok(true))
    }
  }

  test("PFADD with no elements should create empty HyperLogLog") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.pfadd("hll-empty")
        count <- valkey.pfcount("hll-empty")
        _ <- valkey.del("hll-empty")
      } yield {
        assertEquals(result, Ok(true))
        assertEquals(count, Ok(0L))
      }
    }
  }

  // ==================== PFCOUNT ====================

  test("PFCOUNT should return cardinality of a HyperLogLog") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.pfadd("hll-count", "a", "b", "c", "d", "e")
        count <- valkey.pfcount("hll-count")
        _ <- valkey.del("hll-count")
      } yield assertEquals(count, Ok(5L))
    }
  }

  test("PFCOUNT should return 0 for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.pfcount("hll-nonexistent")
      } yield assertEquals(count, Ok(0L))
    }
  }

  test("PFCOUNT should handle duplicates correctly") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.pfadd("hll-dedup", "a", "a", "b", "b", "c")
        count <- valkey.pfcount("hll-dedup")
        _ <- valkey.del("hll-dedup")
      } yield assertEquals(count, Ok(3L))
    }
  }

  test("PFCOUNT with multiple keys should return union cardinality") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.pfadd("hll-union1", "a", "b", "c")
        _ <- valkey.pfadd("hll-union2", "c", "d", "e")
        count <- valkey.pfcount("hll-union1", "hll-union2")
        _ <- valkey.del("hll-union1", "hll-union2")
      } yield assertEquals(count, Ok(5L))
    }
  }

  // ==================== PFMERGE ====================

  test("PFMERGE should merge multiple HyperLogLogs") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.pfadd("hll-src1", "a", "b", "c")
        _ <- valkey.pfadd("hll-src2", "c", "d", "e")
        mergeResult <- valkey.pfmerge("hll-dest", "hll-src1", "hll-src2")
        count <- valkey.pfcount("hll-dest")
        _ <- valkey.del("hll-src1", "hll-src2", "hll-dest")
      } yield {
        assertEquals(mergeResult, Ok(()))
        assertEquals(count, Ok(5L))
      }
    }
  }

  test("PFMERGE should merge into existing destination") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.pfadd("hll-mdest", "x", "y", "z")
        _ <- valkey.pfadd("hll-msrc", "a", "b")
        _ <- valkey.pfmerge("hll-mdest", "hll-msrc")
        count <- valkey.pfcount("hll-mdest")
        _ <- valkey.del("hll-mdest", "hll-msrc")
      } yield assertEquals(count, Ok(5L))
    }
  }

  test("PFMERGE with non-existent sources should create empty HyperLogLog") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.pfmerge("hll-empty-merge", "hll-nosrc1", "hll-nosrc2")
        count <- valkey.pfcount("hll-empty-merge")
        _ <- valkey.del("hll-empty-merge")
      } yield assertEquals(count, Ok(0L))
    }
  }

  // ==================== Complex workflows ====================

  test("HLL workflow: add, count, merge, count") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.pfadd("hll-wf1", "user:1", "user:2", "user:3")
        _ <- valkey.pfadd("hll-wf2", "user:3", "user:4", "user:5")
        c1 <- valkey.pfcount("hll-wf1")
        c2 <- valkey.pfcount("hll-wf2")
        _ <- valkey.pfmerge("hll-wf-all", "hll-wf1", "hll-wf2")
        cAll <- valkey.pfcount("hll-wf-all")
        _ <- valkey.del("hll-wf1", "hll-wf2", "hll-wf-all")
      } yield {
        assertEquals(c1, Ok(3L))
        assertEquals(c2, Ok(3L))
        assertEquals(cAll, Ok(5L))
      }
    }
  }
}
