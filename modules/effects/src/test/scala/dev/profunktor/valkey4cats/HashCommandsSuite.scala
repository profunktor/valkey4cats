package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

import dev.profunktor.valkey4cats.arguments.{
  ExpireCondition,
  ExpirySet,
  FieldCondition,
  HGetExExpiry
}

class HashCommandsSuite extends ValkeyTestSuite {

  test("HSET should set a single field-value pair") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.hset("hash1", Map("field1" -> "value1"))
        value <- valkey.hget("hash1", "field1")
        _ <- valkey.del("hash1")
      } yield {
        assertEquals(count, Ok(1L))
        assertEquals(value, Ok(Some("value1")))
      }
    }
  }

  test("HSET should set multiple field-value pairs") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.hset(
          "hash2",
          Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3")
        )
        _ <- valkey.del("hash2")
      } yield assertEquals(count, Ok(3L))
    }
  }

  test("HSET should update existing field and return 0") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hash3", Map("field" -> "original"))
        count <- valkey.hset("hash3", Map("field" -> "updated"))
        value <- valkey.hget("hash3", "field")
        _ <- valkey.del("hash3")
      } yield {
        assertEquals(count, Ok(0L))
        assertEquals(value, Ok(Some("updated")))
      }
    }
  }

  test("HGET should return None for non-existent field") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hash4", Map("exists" -> "value"))
        result <- valkey.hget("hash4", "does-not-exist")
        _ <- valkey.del("hash4")
      } yield assertEquals(result, Ok(None))
    }
  }

  test("HGET should return None for non-existent hash") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.hget("non-existent-hash", "field")
      } yield assertEquals(result, Ok(None))
    }
  }

  test("HGETALL should return all field-value pairs") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hash5", Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3"))
        result <- valkey.hgetall("hash5")
        _ <- valkey.del("hash5")
      } yield {
        val Ok(m) = result: @unchecked
        assertEquals(m.size, 3)
        assertEquals(m("f1"), "v1")
        assertEquals(m("f2"), "v2")
        assertEquals(m("f3"), "v3")
      }
    }
  }

  test("HGETALL should return empty map for non-existent hash") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.hgetall("non-existent")
      } yield assertEquals(result, Ok(Map.empty[String, String]))
    }
  }

  test("HMGET should return values for multiple fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hash6", Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3"))
        result <- valkey.hmget("hash6", "f1", "f2", "f3")
        _ <- valkey.del("hash6")
      } yield {
        assertEquals(result, Ok(List(Some("v1"), Some("v2"), Some("v3"))))
      }
    }
  }

  test("HMGET should return None for non-existent fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hash7", Map("exists" -> "value"))
        result <- valkey.hmget("hash7", "exists", "missing1", "missing2")
        _ <- valkey.del("hash7")
      } yield {
        assertEquals(result, Ok(List(Some("value"), None, None)))
      }
    }
  }

  test("HDEL should delete specified fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hash8", Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3"))
        count <- valkey.hdel("hash8", "f1", "f2")
        remaining <- valkey.hgetall("hash8")
        _ <- valkey.del("hash8")
      } yield {
        assertEquals(count, Ok(2L))
        assertEquals(remaining, Ok(Map("f3" -> "v3")))
      }
    }
  }

  test("HDEL should return 0 for non-existent fields") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.hdel("non-existent", "field")
      } yield assertEquals(count, Ok(0L))
    }
  }

  test("HEXISTS should return true for existing field") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hash9", Map("field" -> "value"))
        exists <- valkey.hexists("hash9", "field")
        _ <- valkey.del("hash9")
      } yield assertEquals(exists, Ok(true))
    }
  }

  test("HEXISTS should return false for non-existent field") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hash10", Map("field" -> "value"))
        exists <- valkey.hexists("hash10", "other-field")
        _ <- valkey.del("hash10")
      } yield assertEquals(exists, Ok(false))
    }
  }

  test("HKEYS should return all field names") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset(
          "hash11",
          Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3")
        )
        keys <- valkey.hkeys("hash11")
        _ <- valkey.del("hash11")
      } yield {
        val Ok(ks) = keys: @unchecked
        assertEquals(ks.toSet, Set("f1", "f2", "f3"))
      }
    }
  }

  test("HVALS should return all values") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset(
          "hash12",
          Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3")
        )
        values <- valkey.hvals("hash12")
        _ <- valkey.del("hash12")
      } yield {
        val Ok(vs) = values: @unchecked
        assertEquals(vs.toSet, Set("v1", "v2", "v3"))
      }
    }
  }

  test("HLEN should return number of fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset(
          "hash13",
          Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3")
        )
        length <- valkey.hlen("hash13")
        _ <- valkey.del("hash13")
      } yield assertEquals(length, Ok(3L))
    }
  }

  test("HLEN should return 0 for non-existent hash") {
    valkeyClient.use { valkey =>
      for {
        length <- valkey.hlen("non-existent")
      } yield assertEquals(length, Ok(0L))
    }
  }

  test("HINCRBY should increment integer field") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hash14", Map("counter" -> "10"))
        result <- valkey.hincrBy("hash14", "counter", 5)
        value <- valkey.hget("hash14", "counter")
        _ <- valkey.del("hash14")
      } yield {
        assertEquals(result, Ok(15L))
        assertEquals(value, Ok(Some("15")))
      }
    }
  }

  test("HINCRBY should initialize non-existent field to increment value") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.hincrBy("hash15", "new-counter", 42)
        _ <- valkey.del("hash15")
      } yield assertEquals(result, Ok(42L))
    }
  }

  test("HINCRBYFLOAT should increment float field") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hash16", Map("price" -> "10.5"))
        result <- valkey.hincrByFloat("hash16", "price", 2.3)
        _ <- valkey.del("hash16")
      } yield {
        val Ok(d) = result: @unchecked
        assertEquals(d, 12.8, 0.001)
      }
    }
  }

  test("HSETNX should set field only if it doesn't exist") {
    valkeyClient.use { valkey =>
      for {
        result1 <- valkey.hsetnx("hash17", "field", "value1")
        result2 <- valkey.hsetnx("hash17", "field", "value2")
        value <- valkey.hget("hash17", "field")
        _ <- valkey.del("hash17")
      } yield {
        assertEquals(result1, Ok(true))
        assertEquals(result2, Ok(false))
        assertEquals(value, Ok(Some("value1")))
      }
    }
  }

  test("HSTRLEN should return length of field value") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hash18", Map("field" -> "Hello"))
        length <- valkey.hstrlen("hash18", "field")
        _ <- valkey.del("hash18")
      } yield assertEquals(length, Ok(5L))
    }
  }

  test("HSTRLEN should return 0 for non-existent field") {
    valkeyClient.use { valkey =>
      for {
        length <- valkey.hstrlen("hash19", "non-existent")
      } yield assertEquals(length, Ok(0L))
    }
  }

  test("HRANDFIELD should return random field from hash") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset(
          "hash20",
          Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3")
        )
        field <- valkey.hrandfield("hash20")
        _ <- valkey.del("hash20")
      } yield {
        val Ok(f) = field: @unchecked
        assert(f.isDefined)
        assert(Set("f1", "f2", "f3").contains(f.get))
      }
    }
  }

  test("HRANDFIELD should return None for non-existent hash") {
    valkeyClient.use { valkey =>
      for {
        field <- valkey.hrandfield("non-existent")
      } yield assertEquals(field, Ok(None))
    }
  }

  test("HRANDFIELDWITHCOUNT should return multiple random fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset(
          "hash21",
          Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3")
        )
        fields <- valkey.hrandfieldWithCount("hash21", 2)
        _ <- valkey.del("hash21")
      } yield {
        val Ok(fs) = fields: @unchecked
        assertEquals(fs.length, 2)
        assert(fs.forall(f => Set("f1", "f2", "f3").contains(f)))
      }
    }
  }

  test("HRANDFIELDWITHCOUNTWITHVALUES should return random field-value pairs") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset(
          "hash22",
          Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3")
        )
        pairs <- valkey.hrandfieldWithCountWithValues("hash22", 2)
        _ <- valkey.del("hash22")
      } yield {
        val Ok(ps) = pairs: @unchecked
        assertEquals(ps.length, 2)
        ps.foreach { case (field, value) =>
          assert(Set("f1", "f2", "f3").contains(field))
          assertEquals(value, s"v${field.last}")
        }
      }
    }
  }

  test("complex workflow: hash as user profile") {
    valkeyClient.use { valkey =>
      for {
        // Create user profile
        _ <- valkey.hset(
          "user:1",
          Map(
            "name" -> "Alice",
            "email" -> "alice@example.com",
            "age" -> "30",
            "score" -> "100"
          )
        )

        // Get specific fields
        name <- valkey.hget("user:1", "name")
        email <- valkey.hget("user:1", "email")

        // Increment score
        newScore <- valkey.hincrBy("user:1", "score", 50)

        // Check if field exists
        hasPhone <- valkey.hexists("user:1", "phone")

        // Get all fields
        allFields <- valkey.hgetall("user:1")

        // Count fields
        fieldCount <- valkey.hlen("user:1")

        // Cleanup
        _ <- valkey.del("user:1")
      } yield {
        assertEquals(name, Ok(Some("Alice")))
        assertEquals(email, Ok(Some("alice@example.com")))
        assertEquals(newScore, Ok(150L))
        assertEquals(hasPhone, Ok(false))
        val Ok(af) = allFields: @unchecked
        assertEquals(af.size, 4)
        assertEquals(fieldCount, Ok(4L))
      }
    }
  }

  test("HSCAN should iterate over hash fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset(
          "hscan-1",
          Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3")
        )
        result <- valkey.hscan("hscan-1", "0")
      } yield {
        val Ok(r) = result: @unchecked
        assertEquals(r.cursor, "0")
        assertEquals(r.values.size, 3)
        assert(r.values.exists(_._1 == "f1"))
        assert(r.values.exists(_._2 == "v2"))
      }
    }
  }

  test("HSCAN on empty hash should return empty list") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.hscan("hscan-nonexistent", "0")
      } yield {
        val Ok(r) = result: @unchecked
        assertEquals(r.cursor, "0")
        assert(r.values.isEmpty)
      }
    }
  }

  // ==================== Hash Field Expiration (Valkey 8.0+) ====================

  test("HEXPIRE should set expiration on hash fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hexp1", Map("f1" -> "v1", "f2" -> "v2"))
        result <- valkey.hexpire("hexp1", 60, "f1", "f2")
        ttls <- valkey.httl("hexp1", "f1", "f2")
        _ <- valkey.del("hexp1")
      } yield {
        val Ok(rs) = result: @unchecked
        assert(rs.forall(_ == 1L))
        val Ok(ts) = ttls: @unchecked
        assert(ts.forall(t => t > 0 && t <= 60))
      }
    }
  }

  test("HEXPIRE with condition should respect NX") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hexp2", Map("f1" -> "v1"))
        r1 <- valkey.hexpire("hexp2", 60, "f1")
        r2 <- valkey.hexpire(
          "hexp2",
          120,
          ExpireCondition.OnlyIfNoExpiry,
          "f1"
        )
        ttls <- valkey.httl("hexp2", "f1")
        _ <- valkey.del("hexp2")
      } yield {
        val Ok(rs1) = r1: @unchecked
        assertEquals(rs1, List(1L))
        val Ok(rs2) = r2: @unchecked
        assertEquals(rs2, List(0L))
        val Ok(ts) = ttls: @unchecked
        assert(ts.head <= 60)
      }
    }
  }

  test("HPEXPIRE should set millisecond expiration on hash fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hpexp1", Map("f1" -> "v1"))
        result <- valkey.hpexpire("hpexp1", 60000, "f1")
        pttls <- valkey.hpttl("hpexp1", "f1")
        _ <- valkey.del("hpexp1")
      } yield {
        val Ok(rs) = result: @unchecked
        assertEquals(rs, List(1L))
        val Ok(ts) = pttls: @unchecked
        assert(ts.head > 0 && ts.head <= 60000)
      }
    }
  }

  test("HEXPIREAT should set Unix timestamp expiration") {
    valkeyClient.use { valkey =>
      val futureTs = System.currentTimeMillis() / 1000 + 300
      for {
        _ <- valkey.hset("hexpat1", Map("f1" -> "v1"))
        result <- valkey.hexpireAt("hexpat1", futureTs, "f1")
        ets <- valkey.hexpireTime("hexpat1", "f1")
        _ <- valkey.del("hexpat1")
      } yield {
        val Ok(rs) = result: @unchecked
        assertEquals(rs, List(1L))
        val Ok(ts) = ets: @unchecked
        assertEquals(ts.head, futureTs)
      }
    }
  }

  test("HPEXPIREAT should set Unix millisecond timestamp expiration") {
    valkeyClient.use { valkey =>
      val futureMs = System.currentTimeMillis() + 300000
      for {
        _ <- valkey.hset("hpexpat1", Map("f1" -> "v1"))
        result <- valkey.hpexpireAt("hpexpat1", futureMs, "f1")
        ets <- valkey.hpexpireTime("hpexpat1", "f1")
        _ <- valkey.del("hpexpat1")
      } yield {
        val Ok(rs) = result: @unchecked
        assertEquals(rs, List(1L))
        val Ok(ts) = ets: @unchecked
        assert((ts.head - futureMs).abs < 1000)
      }
    }
  }

  test("HTTL should return -1 for fields without expiry") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("httl1", Map("f1" -> "v1"))
        ttls <- valkey.httl("httl1", "f1")
        _ <- valkey.del("httl1")
      } yield {
        val Ok(ts) = ttls: @unchecked
        assertEquals(ts, List(-1L))
      }
    }
  }

  test("HTTL should return -2 for non-existent fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("httl2", Map("f1" -> "v1"))
        ttls <- valkey.httl("httl2", "missing")
        _ <- valkey.del("httl2")
      } yield {
        val Ok(ts) = ttls: @unchecked
        assertEquals(ts, List(-2L))
      }
    }
  }

  test("HPERSIST should remove expiration from hash fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hpers1", Map("f1" -> "v1", "f2" -> "v2"))
        _ <- valkey.hexpire("hpers1", 60, "f1", "f2")
        result <- valkey.hpersist("hpers1", "f1")
        ttl1 <- valkey.httl("hpers1", "f1")
        ttl2 <- valkey.httl("hpers1", "f2")
        _ <- valkey.del("hpers1")
      } yield {
        val Ok(rs) = result: @unchecked
        assertEquals(rs, List(1L))
        val Ok(t1) = ttl1: @unchecked
        assertEquals(t1, List(-1L))
        val Ok(t2) = ttl2: @unchecked
        assert(t2.head > 0)
      }
    }
  }

  test("HPERSIST should return -1 for fields without expiry") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hpers2", Map("f1" -> "v1"))
        result <- valkey.hpersist("hpers2", "f1")
        _ <- valkey.del("hpers2")
      } yield {
        val Ok(rs) = result: @unchecked
        assertEquals(rs, List(-1L))
      }
    }
  }

  test("HGETEX should get values and set expiration") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hgetex1", Map("f1" -> "v1", "f2" -> "v2"))
        result <- valkey.hgetex(
          "hgetex1",
          HGetExExpiry.Seconds(60),
          "f1",
          "f2"
        )
        ttls <- valkey.httl("hgetex1", "f1", "f2")
        _ <- valkey.del("hgetex1")
      } yield {
        val Ok(vs) = result: @unchecked
        assertEquals(vs, List(Some("v1"), Some("v2")))
        val Ok(ts) = ttls: @unchecked
        assert(ts.forall(t => t > 0 && t <= 60))
      }
    }
  }

  test("HGETEX should return None for non-existent fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hgetex2", Map("f1" -> "v1"))
        result <- valkey.hgetex(
          "hgetex2",
          HGetExExpiry.Seconds(60),
          "f1",
          "missing"
        )
        _ <- valkey.del("hgetex2")
      } yield {
        val Ok(vs) = result: @unchecked
        assertEquals(vs, List(Some("v1"), None))
      }
    }
  }

  test("HGETEX with Persist should remove expiration") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hgetex3", Map("f1" -> "v1"))
        _ <- valkey.hexpire("hgetex3", 60, "f1")
        result <- valkey.hgetex("hgetex3", HGetExExpiry.Persist, "f1")
        ttls <- valkey.httl("hgetex3", "f1")
        _ <- valkey.del("hgetex3")
      } yield {
        val Ok(vs) = result: @unchecked
        assertEquals(vs, List(Some("v1")))
        val Ok(ts) = ttls: @unchecked
        assertEquals(ts, List(-1L))
      }
    }
  }

  test("HEXPIRE on non-existent field should return -2") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hexp-ne", Map("f1" -> "v1"))
        result <- valkey.hexpire("hexp-ne", 60, "missing")
        _ <- valkey.del("hexp-ne")
      } yield {
        val Ok(rs) = result: @unchecked
        assertEquals(rs, List(-2L))
      }
    }
  }

  test("HEXPIRETIME should return -1 for fields without expiry") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("het1", Map("f1" -> "v1"))
        result <- valkey.hexpireTime("het1", "f1")
        _ <- valkey.del("het1")
      } yield {
        val Ok(rs) = result: @unchecked
        assertEquals(rs, List(-1L))
      }
    }
  }

  test("HPEXPIRETIME should return -1 for fields without expiry") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hpet1", Map("f1" -> "v1"))
        result <- valkey.hpexpireTime("hpet1", "f1")
        _ <- valkey.del("hpet1")
      } yield {
        val Ok(rs) = result: @unchecked
        assertEquals(rs, List(-1L))
      }
    }
  }

  test("complex workflow: hash field expiration lifecycle") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset(
          "hexp-wf",
          Map("name" -> "Alice", "email" -> "a@b.com", "token" -> "abc123")
        )
        _ <- valkey.hexpire("hexp-wf", 3600, "token")

        ttls <- valkey.httl("hexp-wf", "name", "token")
        _ <- valkey.hpersist("hexp-wf", "token")
        ttlsAfter <- valkey.httl("hexp-wf", "name", "token")

        vals <- valkey.hgetex(
          "hexp-wf",
          HGetExExpiry.Seconds(120),
          "name",
          "email"
        )
        ttlsFinal <- valkey.httl("hexp-wf", "name", "email", "token")

        _ <- valkey.del("hexp-wf")
      } yield {
        val Ok(t1) = ttls: @unchecked
        assertEquals(t1(0), -1L)
        assert(t1(1) > 0 && t1(1) <= 3600)

        val Ok(t2) = ttlsAfter: @unchecked
        assertEquals(t2(0), -1L)
        assertEquals(t2(1), -1L)

        val Ok(vs) = vals: @unchecked
        assertEquals(vs, List(Some("Alice"), Some("a@b.com")))

        val Ok(t3) = ttlsFinal: @unchecked
        assert(t3(0) > 0 && t3(0) <= 120)
        assert(t3(1) > 0 && t3(1) <= 120)
        assertEquals(t3(2), -1L)
      }
    }
  }

  // ==================== HSETEX (Valkey 8.1+) ====================

  test("HSETEX should set fields with expiration") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.hsetex(
          "hsetex1",
          Map("f1" -> "v1", "f2" -> "v2"),
          ExpirySet.Seconds(60)
        )
        values <- valkey.hmget("hsetex1", "f1", "f2")
        ttls <- valkey.httl("hsetex1", "f1", "f2")
        _ <- valkey.del("hsetex1")
      } yield {
        val Ok(c) = count: @unchecked
        assert(c >= 0)
        assertEquals(values, Ok(List(Some("v1"), Some("v2"))))
        val Ok(ts) = ttls: @unchecked
        assert(ts.forall(t => t > 0 && t <= 60))
      }
    }
  }

  test("HSETEX should update existing fields and retain expiry") {
    valkeyClient.use { valkey =>
      for {
        c1 <- valkey.hsetex(
          "hsetex2",
          Map("f1" -> "v1"),
          ExpirySet.Seconds(60)
        )
        c2 <- valkey.hsetex(
          "hsetex2",
          Map("f1" -> "updated", "f2" -> "v2"),
          ExpirySet.Seconds(120)
        )
        values <- valkey.hmget("hsetex2", "f1", "f2")
        ttls <- valkey.httl("hsetex2", "f1", "f2")
        _ <- valkey.del("hsetex2")
      } yield {
        val Ok(n1) = c1: @unchecked
        assert(n1 >= 0)
        val Ok(n2) = c2: @unchecked
        assert(n2 >= 0)
        assertEquals(values, Ok(List(Some("updated"), Some("v2"))))
        val Ok(ts) = ttls: @unchecked
        assert(ts.forall(t => t > 0 && t <= 120))
      }
    }
  }

  test("HSETEX with OnlyIfNoneExist should skip existing fields") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.hset("hsetex3", Map("f1" -> "original"))
        count <- valkey.hsetex(
          "hsetex3",
          Map("f1" -> "new", "f2" -> "v2"),
          ExpirySet.Seconds(60),
          FieldCondition.OnlyIfNoneExist
        )
        values <- valkey.hmget("hsetex3", "f1", "f2")
        _ <- valkey.del("hsetex3")
      } yield {
        val Ok(c) = count: @unchecked
        assert(c >= 0)
        assertEquals(values, Ok(List(Some("original"), None)))
      }
    }
  }

  test("HSETEX with millisecond expiry") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.hsetex(
          "hsetex4",
          Map("f1" -> "v1"),
          ExpirySet.Milliseconds(60000)
        )
        pttls <- valkey.hpttl("hsetex4", "f1")
        _ <- valkey.del("hsetex4")
      } yield {
        assertEquals(count, Ok(1L))
        val Ok(ts) = pttls: @unchecked
        assert(ts.head > 0 && ts.head <= 60000)
      }
    }
  }

  test("HSETEX with empty map should return 0") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.hsetex(
          "hsetex5",
          Map.empty[String, String],
          ExpirySet.Seconds(60)
        )
      } yield assertEquals(count, Ok(0L))
    }
  }
}
