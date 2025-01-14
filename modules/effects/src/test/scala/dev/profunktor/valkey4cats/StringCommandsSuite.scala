package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.arguments.GetExExpiry
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

class StringCommandsSuite extends ValkeyTestSuite {

  // ==================== GET / SET ====================

  test("GET should return None for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.get("non-existent-key")
      } yield assertEquals(result, Ok(None))
    }
  }

  test("SET and GET should work for simple string") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("test-key", "test-value")
        result <- valkey.get("test-key")
        _ <- valkey.del("test-key")
      } yield assertEquals(result, Ok(Some("test-value")))
    }
  }

  test("SET and GET should handle UTF-8 characters") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("utf8-key", "Hello 世界 🌍")
        result <- valkey.get("utf8-key")
        _ <- valkey.del("utf8-key")
      } yield assertEquals(result, Ok(Some("Hello 世界 🌍")))
    }
  }

  test("SET should overwrite existing value") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("overwrite-key", "original")
        _ <- valkey.set("overwrite-key", "updated")
        result <- valkey.get("overwrite-key")
        _ <- valkey.del("overwrite-key")
      } yield assertEquals(result, Ok(Some("updated")))
    }
  }

  // ==================== MGET / MSET ====================

  test("MGET should return values for existing keys") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("key1", "value1")
        _ <- valkey.set("key2", "value2")
        _ <- valkey.set("key3", "value3")
        result <- valkey.mGet(Set("key1", "key2", "key3"))
        _ <- valkey.del("key1", "key2", "key3")
      } yield {
        val Ok(m) = result: @unchecked
        assertEquals(m.size, 3)
        assertEquals(m("key1"), "value1")
        assertEquals(m("key2"), "value2")
        assertEquals(m("key3"), "value3")
      }
    }
  }

  test("MGET should handle mix of existing and non-existing keys") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("exists", "value")
        result <- valkey.mGet(Set("exists", "does-not-exist"))
        _ <- valkey.del("exists")
      } yield {
        val Ok(m) = result: @unchecked
        assertEquals(m.size, 1)
        assertEquals(m.get("exists"), Some("value"))
        assertEquals(m.get("does-not-exist"), None)
      }
    }
  }

  test("MSET should set multiple key-value pairs") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.mSet(Map("mset1" -> "v1", "mset2" -> "v2", "mset3" -> "v3"))
        v1 <- valkey.get("mset1")
        v2 <- valkey.get("mset2")
        v3 <- valkey.get("mset3")
        _ <- valkey.del("mset1", "mset2", "mset3")
      } yield {
        assertEquals(v1, Ok(Some("v1")))
        assertEquals(v2, Ok(Some("v2")))
        assertEquals(v3, Ok(Some("v3")))
      }
    }
  }

  // ==================== INCR / INCRBY / DECR / DECRBY ====================

  test("INCR should increment a numeric string") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("counter", "10")
        result <- valkey.incr("counter")
        _ <- valkey.del("counter")
      } yield assertEquals(result, Ok(11L))
    }
  }

  test("INCR should initialize non-existent key to 1") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.incr("new-counter")
        _ <- valkey.del("new-counter")
      } yield assertEquals(result, Ok(1L))
    }
  }

  test("INCRBY should increment by specified amount") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("incrby-test", "100")
        result <- valkey.incrBy("incrby-test", 50)
        _ <- valkey.del("incrby-test")
      } yield assertEquals(result, Ok(150L))
    }
  }

  test("DECR should decrement a numeric string") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("decr-test", "10")
        result <- valkey.decr("decr-test")
        _ <- valkey.del("decr-test")
      } yield assertEquals(result, Ok(9L))
    }
  }

  test("DECRBY should decrement by specified amount") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("decrby-test", "100")
        result <- valkey.decrBy("decrby-test", 30)
        _ <- valkey.del("decrby-test")
      } yield assertEquals(result, Ok(70L))
    }
  }

  // ==================== APPEND / STRLEN ====================

  test("APPEND should append to existing value") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("append-test", "Hello")
        length <- valkey.append("append-test", " World")
        result <- valkey.get("append-test")
        _ <- valkey.del("append-test")
      } yield {
        assertEquals(length, Ok(11L))
        assertEquals(result, Ok(Some("Hello World")))
      }
    }
  }

  test("APPEND should create key if it doesn't exist") {
    valkeyClient.use { valkey =>
      for {
        length <- valkey.append("new-append", "Created")
        result <- valkey.get("new-append")
        _ <- valkey.del("new-append")
      } yield {
        assertEquals(length, Ok(7L))
        assertEquals(result, Ok(Some("Created")))
      }
    }
  }

  test("STRLEN should return length of string value") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("strlen-test", "Hello")
        length <- valkey.strlen("strlen-test")
        _ <- valkey.del("strlen-test")
      } yield assertEquals(length, Ok(5L))
    }
  }

  test("STRLEN should return 0 for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        length <- valkey.strlen("does-not-exist")
      } yield assertEquals(length, Ok(0L))
    }
  }

  // ==================== GETEX ====================

  test("GETEX should return value without changing expiry") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("getex-basic", "value")
        result <- valkey.getEx("getex-basic")
        ttl <- valkey.ttl("getex-basic")
        _ <- valkey.del("getex-basic")
      } yield {
        assertEquals(result, Ok(Some("value")))
        assertEquals(ttl, Ok(-1L))
      }
    }
  }

  test("GETEX should return None for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.getEx("no-such-getex")
      } yield assertEquals(result, Ok(None))
    }
  }

  test("GETEX with Seconds should set expiry") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("getex-seconds", "value")
        result <- valkey.getEx("getex-seconds", GetExExpiry.Seconds(60))
        ttl <- valkey.ttl("getex-seconds")
        _ <- valkey.del("getex-seconds")
      } yield {
        assertEquals(result, Ok(Some("value")))
        val Ok(t) = ttl: @unchecked
        assert(t > 0L && t <= 60L)
      }
    }
  }

  test("GETEX with Milliseconds should set expiry in ms") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("getex-millis", "value")
        result <- valkey.getEx("getex-millis", GetExExpiry.Milliseconds(60000))
        pttl <- valkey.pttl("getex-millis")
        _ <- valkey.del("getex-millis")
      } yield {
        assertEquals(result, Ok(Some("value")))
        val Ok(t) = pttl: @unchecked
        assert(t > 0L && t <= 60000L)
      }
    }
  }

  test("GETEX with UnixSeconds should set expiry to timestamp") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("getex-unix", "value")
        futureTs = System.currentTimeMillis() / 1000 + 120
        result <- valkey.getEx("getex-unix", GetExExpiry.UnixSeconds(futureTs))
        ttl <- valkey.ttl("getex-unix")
        _ <- valkey.del("getex-unix")
      } yield {
        assertEquals(result, Ok(Some("value")))
        val Ok(t) = ttl: @unchecked
        assert(t > 0L && t <= 120L)
      }
    }
  }

  test("GETEX with Persist should remove expiry") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("getex-persist", "value")
        _ <- valkey.expire("getex-persist", 60)
        ttlBefore <- valkey.ttl("getex-persist")
        result <- valkey.getEx("getex-persist", GetExExpiry.Persist)
        ttlAfter <- valkey.ttl("getex-persist")
        _ <- valkey.del("getex-persist")
      } yield {
        val Ok(before) = ttlBefore: @unchecked
        assert(before > 0L)
        assertEquals(result, Ok(Some("value")))
        assertEquals(ttlAfter, Ok(-1L))
      }
    }
  }

  // ==================== GETDEL ====================

  test("GETDEL should return value and delete key") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("getdel-test", "value")
        result <- valkey.getDel("getdel-test")
        exists <- valkey.exists("getdel-test")
      } yield {
        assertEquals(result, Ok(Some("value")))
        assertEquals(exists, Ok(false))
      }
    }
  }

  test("GETDEL should return None for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.getDel("no-such-getdel")
      } yield assertEquals(result, Ok(None))
    }
  }

  test("GETDEL idempotency — second call returns None") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("getdel-idem", "value")
        first <- valkey.getDel("getdel-idem")
        second <- valkey.getDel("getdel-idem")
      } yield {
        assertEquals(first, Ok(Some("value")))
        assertEquals(second, Ok(None))
      }
    }
  }

  // ==================== INCRBYFLOAT ====================

  test("INCRBYFLOAT should increment by floating-point amount") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("float-counter", "10.5")
        result <- valkey.incrByFloat("float-counter", 1.5)
        _ <- valkey.del("float-counter")
      } yield assertEquals(result, Ok(12.0))
    }
  }

  test("INCRBYFLOAT should initialize non-existent key") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.incrByFloat("new-float", 3.14)
        _ <- valkey.del("new-float")
      } yield assertEquals(result, Ok(3.14))
    }
  }

  test("INCRBYFLOAT with negative amount should decrement") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("float-dec", "10.0")
        result <- valkey.incrByFloat("float-dec", -2.5)
        _ <- valkey.del("float-dec")
      } yield assertEquals(result, Ok(7.5))
    }
  }

  // ==================== SETNX ====================

  test("SETNX should set key only if not exists") {
    valkeyClient.use { valkey =>
      for {
        first <- valkey.setNx("setnx-test", "value1")
        second <- valkey.setNx("setnx-test", "value2")
        result <- valkey.get("setnx-test")
        _ <- valkey.del("setnx-test")
      } yield {
        assertEquals(first, Ok(true))
        assertEquals(second, Ok(false))
        assertEquals(result, Ok(Some("value1")))
      }
    }
  }

  test("SETNX should succeed for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        set <- valkey.setNx("setnx-new", "value")
        result <- valkey.get("setnx-new")
        _ <- valkey.del("setnx-new")
      } yield {
        assertEquals(set, Ok(true))
        assertEquals(result, Ok(Some("value")))
      }
    }
  }

  // ==================== MSETNX ====================

  test("MSETNX should set all keys when none exist") {
    valkeyClient.use { valkey =>
      for {
        set <- valkey.mSetNx(Map("msetnx1" -> "v1", "msetnx2" -> "v2"))
        v1 <- valkey.get("msetnx1")
        v2 <- valkey.get("msetnx2")
        _ <- valkey.del("msetnx1", "msetnx2")
      } yield {
        assertEquals(set, Ok(true))
        assertEquals(v1, Ok(Some("v1")))
        assertEquals(v2, Ok(Some("v2")))
      }
    }
  }

  test("MSETNX should set no keys when any exist") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("msetnx-exists", "existing")
        set <- valkey.mSetNx(
          Map("msetnx-exists" -> "new", "msetnx-new" -> "new")
        )
        existingVal <- valkey.get("msetnx-exists")
        newVal <- valkey.get("msetnx-new")
        _ <- valkey.del("msetnx-exists")
      } yield {
        assertEquals(set, Ok(false))
        assertEquals(existingVal, Ok(Some("existing")))
        assertEquals(newVal, Ok(None))
      }
    }
  }

  // ==================== Complex workflows ====================

  test("complex workflow: counters with conditions") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("workflow-counter", "0")
        c1 <- valkey.incr("workflow-counter")
        c2 <- valkey.incrBy("workflow-counter", 10)
        c3 <- valkey.incr("workflow-counter")
        finalValue <- valkey.get("workflow-counter")
        _ <- valkey.del("workflow-counter")
      } yield {
        assertEquals(c1, Ok(1L))
        assertEquals(c2, Ok(11L))
        assertEquals(c3, Ok(12L))
        assertEquals(finalValue, Ok(Some("12")))
      }
    }
  }

  test("getEx + ttl round-trip") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("getex-roundtrip", "value")
        _ <- valkey.getEx("getex-roundtrip", GetExExpiry.Seconds(120))
        ttl <- valkey.ttl("getex-roundtrip")
        _ <- valkey.getEx("getex-roundtrip", GetExExpiry.Persist)
        ttlAfter <- valkey.ttl("getex-roundtrip")
        _ <- valkey.del("getex-roundtrip")
      } yield {
        val Ok(t) = ttl: @unchecked
        assert(t > 0L && t <= 120L)
        assertEquals(ttlAfter, Ok(-1L))
      }
    }
  }

  // ==================== GETRANGE / SETRANGE ====================

  test("GETRANGE should return substring of value") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("getrange-test", "Hello World")
        result <- valkey.getRange("getrange-test", 0, 4)
        _ <- valkey.del("getrange-test")
      } yield assertEquals(result, Ok("Hello"))
    }
  }

  test("GETRANGE with negative indices should count from end") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("getrange-neg", "Hello World")
        result <- valkey.getRange("getrange-neg", -5, -1)
        _ <- valkey.del("getrange-neg")
      } yield assertEquals(result, Ok("World"))
    }
  }

  test("GETRANGE beyond string length should return available portion") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("getrange-beyond", "Hi")
        result <- valkey.getRange("getrange-beyond", 0, 100)
        _ <- valkey.del("getrange-beyond")
      } yield assertEquals(result, Ok("Hi"))
    }
  }

  test("GETRANGE on non-existent key should return empty string") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.getRange("getrange-nokey", 0, 10)
      } yield assertEquals(result, Ok(""))
    }
  }

  test("SETRANGE should overwrite part of a string") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("setrange-test", "Hello World")
        length <- valkey.setRange("setrange-test", 6, "Redis")
        result <- valkey.get("setrange-test")
        _ <- valkey.del("setrange-test")
      } yield {
        assertEquals(length, Ok(11L))
        assertEquals(result, Ok(Some("Hello Redis")))
      }
    }
  }

  test("SETRANGE should pad with zero bytes if offset is past end") {
    valkeyClient.use { valkey =>
      for {
        length <- valkey.setRange("setrange-pad", 5, "Hi")
        result <- valkey.strlen("setrange-pad")
        _ <- valkey.del("setrange-pad")
      } yield {
        assertEquals(length, Ok(7L))
        assertEquals(result, Ok(7L))
      }
    }
  }

  // ==================== LCS / LCSLEN ====================

  test("LCS should return longest common substring") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("lcs1", "ohmytext")
        _ <- valkey.set("lcs2", "mynewtext")
        result <- valkey.lcs("lcs1", "lcs2")
        _ <- valkey.del("lcs1", "lcs2")
      } yield assertEquals(result, Ok("mytext"))
    }
  }

  test("LCS should return empty string for no common substring") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("lcs-none1", "abc")
        _ <- valkey.set("lcs-none2", "xyz")
        result <- valkey.lcs("lcs-none1", "lcs-none2")
        _ <- valkey.del("lcs-none1", "lcs-none2")
      } yield assertEquals(result, Ok(""))
    }
  }

  test("LCSLEN should return length of longest common substring") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("lcslen1", "ohmytext")
        _ <- valkey.set("lcslen2", "mynewtext")
        result <- valkey.lcsLen("lcslen1", "lcslen2")
        _ <- valkey.del("lcslen1", "lcslen2")
      } yield assertEquals(result, Ok(6L))
    }
  }

  test("LCSLEN should return 0 for no common substring") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("lcslen-none1", "abc")
        _ <- valkey.set("lcslen-none2", "xyz")
        result <- valkey.lcsLen("lcslen-none1", "lcslen-none2")
        _ <- valkey.del("lcslen-none1", "lcslen-none2")
      } yield assertEquals(result, Ok(0L))
    }
  }
}
