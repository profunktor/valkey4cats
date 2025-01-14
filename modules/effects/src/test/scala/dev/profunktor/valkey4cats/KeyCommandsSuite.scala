package dev.profunktor.valkey4cats

import cats.effect.IO
import dev.profunktor.valkey4cats.arguments.ExpireCondition
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok
import dev.profunktor.valkey4cats.results.ClusterScanCursor

class KeyCommandsSuite extends ValkeyTestSuite {

  // ==================== DEL ====================

  test("DEL should delete a single key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("del-test", "value")
        deleted <- valkey.del("del-test")
        result <- valkey.get("del-test")
      } yield {
        assertEquals(deleted, Ok(1L))
        assertEquals(result, Ok(None))
      }
    }
  }

  test("DEL should delete multiple keys") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("del1", "v1")
        _ <- valkey.set("del2", "v2")
        _ <- valkey.set("del3", "v3")
        deleted <- valkey.del("del1", "del2", "del3")
      } yield assertEquals(deleted, Ok(3L))
    }
  }

  test("DEL should return 0 for non-existent keys") {
    valkeyClient.use { valkey =>
      for {
        deleted <- valkey.del("does-not-exist")
      } yield assertEquals(deleted, Ok(0L))
    }
  }

  test("DEL should handle mix of existing and non-existing keys") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("exists", "value")
        deleted <- valkey.del("exists", "does-not-exist")
      } yield assertEquals(deleted, Ok(1L))
    }
  }

  // ==================== EXISTS ====================

  test("EXISTS should return true for existing key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("exists-test", "value")
        exists <- valkey.exists("exists-test")
        _ <- valkey.del("exists-test")
      } yield assertEquals(exists, Ok(true))
    }
  }

  test("EXISTS should return false for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        exists <- valkey.exists("does-not-exist")
      } yield assertEquals(exists, Ok(false))
    }
  }

  test("EXISTSMANY should count existing keys") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("key1", "v1")
        _ <- valkey.set("key2", "v2")
        count <- valkey.existsMany("key1", "key2", "key3")
        _ <- valkey.del("key1", "key2")
      } yield assertEquals(count, Ok(2L))
    }
  }

  test("EXISTSMANY should return 0 when no keys exist") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.existsMany("none1", "none2", "none3")
      } yield assertEquals(count, Ok(0L))
    }
  }

  // ==================== UNLINK ====================

  test("UNLINK should remove a key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("unlink-test", "value")
        unlinked <- valkey.unlink("unlink-test")
        exists <- valkey.exists("unlink-test")
      } yield {
        assertEquals(unlinked, Ok(1L))
        assertEquals(exists, Ok(false))
      }
    }
  }

  test("UNLINK should remove multiple keys") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("unlink1", "v1")
        _ <- valkey.set("unlink2", "v2")
        _ <- valkey.set("unlink3", "v3")
        unlinked <- valkey.unlink("unlink1", "unlink2", "unlink3")
      } yield assertEquals(unlinked, Ok(3L))
    }
  }

  test("UNLINK should return 0 for non-existent keys") {
    valkeyClient.use { valkey =>
      for {
        unlinked <- valkey.unlink("no-such-key")
      } yield assertEquals(unlinked, Ok(0L))
    }
  }

  // ==================== EXPIRE / TTL ====================

  test("EXPIRE should set expiry and TTL should return it") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("expire-test", "value")
        set <- valkey.expire("expire-test", 60)
        ttlResult <- valkey.ttl("expire-test")
        _ <- valkey.del("expire-test")
      } yield {
        assertEquals(set, Ok(true))
        val Ok(ttl) = ttlResult: @unchecked
        assert(ttl > 0L && ttl <= 60L)
      }
    }
  }

  test("EXPIRE should return false for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        set <- valkey.expire("no-such-key", 60)
      } yield assertEquals(set, Ok(false))
    }
  }

  test("EXPIRE with OnlyIfNoExpiry should set expiry only when none exists") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("expire-nx", "value")
        first <- valkey.expire("expire-nx", 100, ExpireCondition.OnlyIfNoExpiry)
        second <- valkey.expire(
          "expire-nx",
          200,
          ExpireCondition.OnlyIfNoExpiry
        )
        ttlResult <- valkey.ttl("expire-nx")
        _ <- valkey.del("expire-nx")
      } yield {
        assertEquals(first, Ok(true))
        assertEquals(second, Ok(false))
        val Ok(ttl) = ttlResult: @unchecked
        assert(ttl <= 100L)
      }
    }
  }

  test("EXPIRE with OnlyIfHasExpiry should update only when expiry exists") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("expire-xx", "value")
        noExpiry <- valkey.expire(
          "expire-xx",
          100,
          ExpireCondition.OnlyIfHasExpiry
        )
        _ <- valkey.expire("expire-xx", 50)
        withExpiry <- valkey.expire(
          "expire-xx",
          200,
          ExpireCondition.OnlyIfHasExpiry
        )
        _ <- valkey.del("expire-xx")
      } yield {
        assertEquals(noExpiry, Ok(false))
        assertEquals(withExpiry, Ok(true))
      }
    }
  }

  test("EXPIRE with OnlyIfGreater should only increase TTL") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("expire-gt", "value")
        _ <- valkey.expire("expire-gt", 100)
        smaller <- valkey.expire("expire-gt", 50, ExpireCondition.OnlyIfGreater)
        larger <- valkey.expire("expire-gt", 200, ExpireCondition.OnlyIfGreater)
        _ <- valkey.del("expire-gt")
      } yield {
        assertEquals(smaller, Ok(false))
        assertEquals(larger, Ok(true))
      }
    }
  }

  // ==================== PEXPIRE / PTTL ====================

  test("PEXPIRE should set expiry in milliseconds") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("pexpire-test", "value")
        set <- valkey.pexpire("pexpire-test", 60000)
        pttlResult <- valkey.pttl("pexpire-test")
        _ <- valkey.del("pexpire-test")
      } yield {
        assertEquals(set, Ok(true))
        val Ok(pttl) = pttlResult: @unchecked
        assert(pttl > 0L && pttl <= 60000L)
      }
    }
  }

  test("PEXPIRE with condition should work") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("pexpire-cond", "value")
        first <- valkey.pexpire(
          "pexpire-cond",
          100000,
          ExpireCondition.OnlyIfNoExpiry
        )
        second <- valkey.pexpire(
          "pexpire-cond",
          200000,
          ExpireCondition.OnlyIfNoExpiry
        )
        _ <- valkey.del("pexpire-cond")
      } yield {
        assertEquals(first, Ok(true))
        assertEquals(second, Ok(false))
      }
    }
  }

  // ==================== EXPIREAT / PEXPIREAT ====================

  test("EXPIREAT should set expiry to a Unix timestamp") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("expireat-test", "value")
        futureTs = System.currentTimeMillis() / 1000 + 120
        set <- valkey.expireAt("expireat-test", futureTs)
        ttlResult <- valkey.ttl("expireat-test")
        _ <- valkey.del("expireat-test")
      } yield {
        assertEquals(set, Ok(true))
        val Ok(ttl) = ttlResult: @unchecked
        assert(ttl > 0L && ttl <= 120L)
      }
    }
  }

  test("EXPIREAT with condition should work") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("expireat-cond", "value")
        futureTs = System.currentTimeMillis() / 1000 + 120
        first <- valkey.expireAt(
          "expireat-cond",
          futureTs,
          ExpireCondition.OnlyIfNoExpiry
        )
        second <- valkey.expireAt(
          "expireat-cond",
          futureTs + 60,
          ExpireCondition.OnlyIfNoExpiry
        )
        _ <- valkey.del("expireat-cond")
      } yield {
        assertEquals(first, Ok(true))
        assertEquals(second, Ok(false))
      }
    }
  }

  test("PEXPIREAT should set expiry to a Unix timestamp in milliseconds") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("pexpireat-test", "value")
        futureMs = System.currentTimeMillis() + 120000
        set <- valkey.pexpireAt("pexpireat-test", futureMs)
        pttlResult <- valkey.pttl("pexpireat-test")
        _ <- valkey.del("pexpireat-test")
      } yield {
        assertEquals(set, Ok(true))
        val Ok(pttl) = pttlResult: @unchecked
        assert(pttl > 0L && pttl <= 120000L)
      }
    }
  }

  test("PEXPIREAT with condition should work") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("pexpireat-cond", "value")
        futureMs = System.currentTimeMillis() + 120000
        first <- valkey.pexpireAt(
          "pexpireat-cond",
          futureMs,
          ExpireCondition.OnlyIfNoExpiry
        )
        second <- valkey.pexpireAt(
          "pexpireat-cond",
          futureMs + 60000,
          ExpireCondition.OnlyIfNoExpiry
        )
        _ <- valkey.del("pexpireat-cond")
      } yield {
        assertEquals(first, Ok(true))
        assertEquals(second, Ok(false))
      }
    }
  }

  // ==================== EXPIRETIME / PEXPIRETIME ====================

  test("EXPIRETIME should return the Unix timestamp of expiration") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("expiretime-test", "value")
        futureTs = System.currentTimeMillis() / 1000 + 120
        _ <- valkey.expireAt("expiretime-test", futureTs)
        result <- valkey.expireTime("expiretime-test")
        _ <- valkey.del("expiretime-test")
      } yield assertEquals(result, Ok(futureTs))
    }
  }

  test("EXPIRETIME should return -1 for key without expiry") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("expiretime-noexp", "value")
        result <- valkey.expireTime("expiretime-noexp")
        _ <- valkey.del("expiretime-noexp")
      } yield assertEquals(result, Ok(-1L))
    }
  }

  test("EXPIRETIME should return -2 for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.expireTime("no-such-key-expiretime")
      } yield assertEquals(result, Ok(-2L))
    }
  }

  test("PEXPIRETIME should return the Unix timestamp in milliseconds") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("pexpiretime-test", "value")
        futureMs = System.currentTimeMillis() + 120000
        _ <- valkey.pexpireAt("pexpiretime-test", futureMs)
        result <- valkey.pexpireTime("pexpiretime-test")
        _ <- valkey.del("pexpiretime-test")
      } yield assertEquals(result, Ok(futureMs))
    }
  }

  // ==================== TTL / PTTL edge cases ====================

  test("TTL should return -2 for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.ttl("no-such-key-ttl")
      } yield assertEquals(result, Ok(-2L))
    }
  }

  test("TTL should return -1 for key without expiry") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("ttl-no-expiry", "value")
        result <- valkey.ttl("ttl-no-expiry")
        _ <- valkey.del("ttl-no-expiry")
      } yield assertEquals(result, Ok(-1L))
    }
  }

  test("PTTL should return -2 for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.pttl("no-such-key-pttl")
      } yield assertEquals(result, Ok(-2L))
    }
  }

  test("PTTL should return -1 for key without expiry") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("pttl-no-expiry", "value")
        result <- valkey.pttl("pttl-no-expiry")
        _ <- valkey.del("pttl-no-expiry")
      } yield assertEquals(result, Ok(-1L))
    }
  }

  // ==================== PERSIST ====================

  test("PERSIST should remove expiry from a key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("persist-test", "value")
        _ <- valkey.expire("persist-test", 60)
        ttlBefore <- valkey.ttl("persist-test")
        persisted <- valkey.persist("persist-test")
        ttlAfter <- valkey.ttl("persist-test")
        _ <- valkey.del("persist-test")
      } yield {
        val Ok(before) = ttlBefore: @unchecked
        assert(before > 0L)
        assertEquals(persisted, Ok(true))
        assertEquals(ttlAfter, Ok(-1L))
      }
    }
  }

  test("PERSIST should return false for key without expiry") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("persist-noexp", "value")
        persisted <- valkey.persist("persist-noexp")
        _ <- valkey.del("persist-noexp")
      } yield assertEquals(persisted, Ok(false))
    }
  }

  test("PERSIST should return false for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        persisted <- valkey.persist("no-such-key-persist")
      } yield assertEquals(persisted, Ok(false))
    }
  }

  // ==================== RENAME / RENAMENX ====================

  test("RENAME should rename a key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("rename-src", "value")
        _ <- valkey.rename("rename-src", "rename-dst")
        srcExists <- valkey.exists("rename-src")
        dstValue <- valkey.get("rename-dst")
        _ <- valkey.del("rename-dst")
      } yield {
        assertEquals(srcExists, Ok(false))
        assertEquals(dstValue, Ok(Some("value")))
      }
    }
  }

  test("RENAME should overwrite destination if it exists") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("rename-src2", "new-value")
        _ <- valkey.set("rename-dst2", "old-value")
        _ <- valkey.rename("rename-src2", "rename-dst2")
        dstValue <- valkey.get("rename-dst2")
        _ <- valkey.del("rename-dst2")
      } yield assertEquals(dstValue, Ok(Some("new-value")))
    }
  }

  test("RENAMENX should rename only if destination does not exist") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("renamenx-src", "value")
        renamed <- valkey.renameNx("renamenx-src", "renamenx-dst")
        dstValue <- valkey.get("renamenx-dst")
        _ <- valkey.del("renamenx-dst")
      } yield {
        assertEquals(renamed, Ok(true))
        assertEquals(dstValue, Ok(Some("value")))
      }
    }
  }

  test("RENAMENX should not rename if destination exists") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("renamenx-src2", "source-val")
        _ <- valkey.set("renamenx-dst2", "dest-val")
        renamed <- valkey.renameNx("renamenx-src2", "renamenx-dst2")
        dstValue <- valkey.get("renamenx-dst2")
        _ <- valkey.del("renamenx-src2", "renamenx-dst2")
      } yield {
        assertEquals(renamed, Ok(false))
        assertEquals(dstValue, Ok(Some("dest-val")))
      }
    }
  }

  // ==================== TYPE ====================

  test("TYPE should return 'string' for a string key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("type-string", "value")
        t <- valkey.typeOf("type-string")
        _ <- valkey.del("type-string")
      } yield assertEquals(t, Ok("string"))
    }
  }

  test("TYPE should return 'list' for a list key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.lpush("type-list", "a")
        t <- valkey.typeOf("type-list")
        _ <- valkey.del("type-list")
      } yield assertEquals(t, Ok("list"))
    }
  }

  test("TYPE should return 'set' for a set key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("type-set", "a")
        t <- valkey.typeOf("type-set")
        _ <- valkey.del("type-set")
      } yield assertEquals(t, Ok("set"))
    }
  }

  test("TYPE should return 'zset' for a sorted set key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("type-zset", Map("a" -> 1.0))
        t <- valkey.typeOf("type-zset")
        _ <- valkey.del("type-zset")
      } yield assertEquals(t, Ok("zset"))
    }
  }

  test("TYPE should return 'hash' for a hash key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("type-hash", Map("field" -> "value"))
        t <- valkey.typeOf("type-hash")
        _ <- valkey.del("type-hash")
      } yield assertEquals(t, Ok("hash"))
    }
  }

  test("TYPE should return 'none' for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        t <- valkey.typeOf("no-such-key-type")
      } yield assertEquals(t, Ok("none"))
    }
  }

  // ==================== OBJECT ENCODING ====================

  test("OBJECT ENCODING should return encoding for string key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("encoding-test", "hello")
        enc <- valkey.objectEncoding("encoding-test")
        _ <- valkey.del("encoding-test")
      } yield {
        val Ok(Some(encoding)) = enc: @unchecked
        assert(encoding == "embstr" || encoding == "raw")
      }
    }
  }

  test("OBJECT ENCODING should return None for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        enc <- valkey.objectEncoding("no-such-key-encoding")
      } yield assertEquals(enc, Ok(None))
    }
  }

  // ==================== TOUCH ====================

  test("TOUCH should update access time for existing keys") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("touch1", "v1")
        _ <- valkey.set("touch2", "v2")
        touched <- valkey.touch("touch1", "touch2")
        _ <- valkey.del("touch1", "touch2")
      } yield assertEquals(touched, Ok(2L))
    }
  }

  test("TOUCH should count only existing keys") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("touch-exists", "v1")
        touched <- valkey.touch("touch-exists", "touch-no-exist")
        _ <- valkey.del("touch-exists")
      } yield assertEquals(touched, Ok(1L))
    }
  }

  test("TOUCH should return 0 for non-existent keys") {
    valkeyClient.use { valkey =>
      for {
        touched <- valkey.touch("no-such-key-touch")
      } yield assertEquals(touched, Ok(0L))
    }
  }

  // ==================== COPY ====================

  test("COPY should copy a key's value") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("copy-src", "value")
        copied <- valkey.copy("copy-src", "copy-dst")
        srcVal <- valkey.get("copy-src")
        dstVal <- valkey.get("copy-dst")
        _ <- valkey.del("copy-src", "copy-dst")
      } yield {
        assertEquals(copied, Ok(true))
        assertEquals(srcVal, Ok(Some("value")))
        assertEquals(dstVal, Ok(Some("value")))
      }
    }
  }

  test("COPY should return false if destination already exists") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("copy-src2", "source")
        _ <- valkey.set("copy-dst2", "existing")
        copied <- valkey.copy("copy-src2", "copy-dst2")
        dstVal <- valkey.get("copy-dst2")
        _ <- valkey.del("copy-src2", "copy-dst2")
      } yield {
        assertEquals(copied, Ok(false))
        assertEquals(dstVal, Ok(Some("existing")))
      }
    }
  }

  test("COPY should return false for non-existent source") {
    valkeyClient.use { valkey =>
      for {
        copied <- valkey.copy("no-such-src", "copy-dst3")
      } yield assertEquals(copied, Ok(false))
    }
  }

  // ==================== Complex workflows ====================

  test("complex workflow: create, check, delete") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.mSet(Map("wf1" -> "v1", "wf2" -> "v2", "wf3" -> "v3"))
        count1 <- valkey.existsMany("wf1", "wf2", "wf3")
        deleted <- valkey.del("wf2")
        count2 <- valkey.existsMany("wf1", "wf2", "wf3")
        exists1 <- valkey.exists("wf1")
        exists2 <- valkey.exists("wf2")
        exists3 <- valkey.exists("wf3")
        _ <- valkey.del("wf1", "wf3")
      } yield {
        assertEquals(count1, Ok(3L))
        assertEquals(deleted, Ok(1L))
        assertEquals(count2, Ok(2L))
        assertEquals(exists1, Ok(true))
        assertEquals(exists2, Ok(false))
        assertEquals(exists3, Ok(true))
      }
    }
  }

  test("expire/persist/ttl lifecycle") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("lifecycle", "value")
        ttl1 <- valkey.ttl("lifecycle")
        _ <- valkey.expire("lifecycle", 120)
        ttl2 <- valkey.ttl("lifecycle")
        _ <- valkey.persist("lifecycle")
        ttl3 <- valkey.ttl("lifecycle")
        _ <- valkey.del("lifecycle")
      } yield {
        assertEquals(ttl1, Ok(-1L))
        val Ok(t2) = ttl2: @unchecked
        assert(t2 > 0L && t2 <= 120L)
        assertEquals(ttl3, Ok(-1L))
      }
    }
  }

  test("rename preserves TTL") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("rename-ttl-src", "value")
        _ <- valkey.expire("rename-ttl-src", 120)
        _ <- valkey.rename("rename-ttl-src", "rename-ttl-dst")
        ttlResult <- valkey.ttl("rename-ttl-dst")
        _ <- valkey.del("rename-ttl-dst")
      } yield {
        val Ok(ttl) = ttlResult: @unchecked
        assert(ttl > 0L && ttl <= 120L)
      }
    }
  }

  test("stress test: delete many keys at once") {
    valkeyClient.use { valkey =>
      for {
        _ <- IO.traverse((1 to 100).toList)(i =>
          valkey.set(s"stress-$i", s"value-$i")
        )
        count1 <- valkey.existsMany((1 to 100).map(i => s"stress-$i")*)
        deleted <- valkey.del((1 to 100).map(i => s"stress-$i")*)
        count2 <- valkey.existsMany((1 to 100).map(i => s"stress-$i")*)
      } yield {
        assertEquals(count1, Ok(100L))
        assertEquals(deleted, Ok(100L))
        assertEquals(count2, Ok(0L))
      }
    }
  }

  // ==================== RANDOMKEY ====================

  test("RANDOMKEY should return a key when database is non-empty") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("rk-test1", "value1")
        _ <- valkey.set("rk-test2", "value2")
        result <- valkey.randomKey
        _ <- valkey.del("rk-test1", "rk-test2")
      } yield {
        val Ok(maybeKey) = result: @unchecked
        assert(maybeKey.isDefined)
      }
    }
  }

  test("RANDOMKEY should return different keys over multiple calls") {
    valkeyClient.use { valkey =>
      for {
        _ <- IO.traverse((1 to 20).toList)(i =>
          valkey.set(s"rk-multi-$i", s"value-$i")
        )
        keys <- IO.traverse((1 to 10).toList)(_ => valkey.randomKey)
        _ <- valkey.del((1 to 20).map(i => s"rk-multi-$i")*)
      } yield {
        val results = keys.collect { case Ok(Some(k)) => k }.toSet
        assert(results.size > 1)
      }
    }
  }

  // ==================== OBJECT IDLETIME / REFCOUNT ====================

  test("OBJECT IDLETIME should return idle time for existing key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("obj-idle", "value")
        result <- valkey.objectIdletime("obj-idle")
        _ <- valkey.del("obj-idle")
      } yield {
        val Ok(Some(idle)) = result: @unchecked
        assert(idle >= 0L)
      }
    }
  }

  test("OBJECT REFCOUNT should return refcount for existing key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("obj-ref", "value")
        result <- valkey.objectRefcount("obj-ref")
        _ <- valkey.del("obj-ref")
      } yield {
        val Ok(Some(refcount)) = result: @unchecked
        assert(refcount >= 1L)
      }
    }
  }

  // ==================== SORT / SORT_RO / SORT STORE ====================

  test("SORT should sort a list of numbers") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("sort-nums", "3", "1", "2", "5", "4")
        sorted <- valkey.sort("sort-nums")
        _ <- valkey.del("sort-nums")
      } yield assertEquals(sorted, Ok(List("1", "2", "3", "4", "5")))
    }
  }

  test("SORT_RO should sort without modifying") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("sort-ro", "3", "1", "2")
        sorted <- valkey.sortReadOnly("sort-ro")
        original <- valkey.lrange("sort-ro", 0, -1)
        _ <- valkey.del("sort-ro")
      } yield {
        assertEquals(sorted, Ok(List("1", "2", "3")))
        assertEquals(original, Ok(List("3", "1", "2")))
      }
    }
  }

  test("SORT STORE should sort and store result") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("sort-src", "3", "1", "2")
        count <- valkey.sortStore("sort-src", "sort-dest")
        result <- valkey.lrange("sort-dest", 0, -1)
        _ <- valkey.del("sort-src", "sort-dest")
      } yield {
        assertEquals(count, Ok(3L))
        assertEquals(result, Ok(List("1", "2", "3")))
      }
    }
  }

  // ==================== DUMP / RESTORE ====================

  test("DUMP and RESTORE should round-trip a value") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("dump-test", "hello-world")
        dumped <- valkey.dump("dump-test")
        _ <- valkey.del("dump-test")
        _ <- {
          val Ok(Some(bytes)) = dumped: @unchecked
          valkey.restore("dump-test", 0L, bytes)
        }
        restored <- valkey.get("dump-test")
        _ <- valkey.del("dump-test")
      } yield assertEquals(restored, Ok(Some("hello-world")))
    }
  }

  test("DUMP should return None for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.dump("dump-nokey")
      } yield assertEquals(result, Ok(None))
    }
  }

  // ==================== WAIT ====================

  test("WAIT should return 0 for standalone server with no replicas") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("wait-test", "value")
        result <- valkey.waitReplicas(1, 100)
        _ <- valkey.del("wait-test")
      } yield assertEquals(result, Ok(0L))
    }
  }

  test("MOVE should move a key to another database") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("move-test", "value")
        moved <- valkey.move("move-test", 1)
        exists <- valkey.exists("move-test")
      } yield {
        assertEquals(moved, Ok(true))
        assertEquals(exists, Ok(false))
      }
    }
  }

  test("MOVE non-existent key should return false") {
    valkeyClient.use { valkey =>
      for {
        moved <- valkey.move("move-nonexistent", 1)
      } yield assertEquals(moved, Ok(false))
    }
  }

  // ==================== SCAN ====================

  test("SCAN should iterate over keys") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("scan-k1", "v1")
        _ <- valkey.set("scan-k2", "v2")
        _ <- valkey.set("scan-k3", "v3")
        result <- valkey.scan("0")
      } yield {
        val Ok(r) = result: @unchecked
        assert(r.values.nonEmpty)
      }
    }
  }

  test("SCAN with match pattern should filter keys") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("scan-pat-a1", "v1")
        _ <- valkey.set("scan-pat-a2", "v2")
        _ <- valkey.set("scan-other-1", "v3")
        result <- valkey.scan("0", "scan-pat-*", 100)
      } yield {
        val Ok(r) = result: @unchecked
        assert(r.values.forall(_.startsWith("scan-pat-")))
      }
    }
  }

  test("SCAN on empty db with non-matching pattern should return empty") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.scan("0", "nonexistent-prefix-*", 100)
      } yield {
        val Ok(r) = result: @unchecked
        assertEquals(r.cursor, "0")
        assert(r.values.isEmpty)
      }
    }
  }

  test(
    "clusterScan on standalone should fail with UnsupportedOperationException"
  ) {
    valkeyClient.use { valkey =>
      val cursor = ClusterScanCursor.initial
      valkey
        .clusterScan(cursor)
        .map(_ => fail("Expected UnsupportedOperationException"))
        .handleError {
          case _: UnsupportedOperationException => ()
          case e                                => fail(s"Unexpected error: $e")
        }
    }
  }
}
