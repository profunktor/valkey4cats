package dev.profunktor.valkey4cats

import cats.effect.IO
import dev.profunktor.valkey4cats.arguments.*
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok
import dev.profunktor.valkey4cats.results.{ClusterScanCursor, InsertResult}

class ClusterCommandsSuite extends ClusterTestSuite {

  // ==================== Connection Commands ====================

  test("PING should work on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.ping
      } yield {
        val Ok(pong) = result: @unchecked
        assertEquals(pong, "PONG")
      }
    }
  }

  test("PING with message should echo on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.ping("cluster-hello")
      } yield {
        val Ok(msg) = result: @unchecked
        assertEquals(msg, "cluster-hello")
      }
    }
  }

  test("ECHO should return the message on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.echo("cluster-echo")
      } yield {
        val Ok(msg) = result: @unchecked
        assertEquals(msg, "cluster-echo")
      }
    }
  }

  test("CLIENT ID should return a positive long on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.clientId
      } yield {
        val Ok(id) = result: @unchecked
        assert(id > 0)
      }
    }
  }

  test("CLIENT GETNAME should return None when no name is set on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.clientGetName
      } yield {
        val Ok(name) = result: @unchecked
        assert(name.isEmpty)
      }
    }
  }

  // ==================== String Commands ====================

  test("SET/GET should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-str-1", "hello")
        result <- valkey.get("cl-str-1")
        _ <- valkey.del("cl-str-1")
      } yield assertEquals(result, Ok(Some("hello")))
    }
  }

  test("GET should return None for non-existent key on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.get("cl-str-nokey")
      } yield assertEquals(result, Ok(None))
    }
  }

  test("SET should overwrite existing value on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-str-ow", "original")
        _ <- valkey.set("cl-str-ow", "updated")
        result <- valkey.get("cl-str-ow")
        _ <- valkey.del("cl-str-ow")
      } yield assertEquals(result, Ok(Some("updated")))
    }
  }

  test("INCR should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-counter", "0")
        r1 <- valkey.incr("cl-counter")
        r2 <- valkey.incrBy("cl-counter", 5)
        _ <- valkey.del("cl-counter")
      } yield {
        assertEquals(r1, Ok(1L))
        assertEquals(r2, Ok(6L))
      }
    }
  }

  test("DECR/DECRBY should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-decr", "100")
        r1 <- valkey.decr("cl-decr")
        r2 <- valkey.decrBy("cl-decr", 30)
        _ <- valkey.del("cl-decr")
      } yield {
        assertEquals(r1, Ok(99L))
        assertEquals(r2, Ok(69L))
      }
    }
  }

  test("INCRBYFLOAT should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-float", "10.5")
        result <- valkey.incrByFloat("cl-float", 1.5)
        _ <- valkey.del("cl-float")
      } yield assertEquals(result, Ok(12.0))
    }
  }

  test("APPEND/STRLEN should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-append", "Hello")
        length <- valkey.append("cl-append", " World")
        strLen <- valkey.strlen("cl-append")
        _ <- valkey.del("cl-append")
      } yield {
        assertEquals(length, Ok(11L))
        assertEquals(strLen, Ok(11L))
      }
    }
  }

  test("GETRANGE/SETRANGE should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-range", "Hello World")
        sub <- valkey.getRange("cl-range", 0, 4)
        _ <- valkey.setRange("cl-range", 6, "Valkey")
        result <- valkey.get("cl-range")
        _ <- valkey.del("cl-range")
      } yield {
        assertEquals(sub, Ok("Hello"))
        assertEquals(result, Ok(Some("Hello Valkey")))
      }
    }
  }

  test("GETEX should set expiry on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-getex", "value")
        result <- valkey.getEx("cl-getex", GetExExpiry.Seconds(60))
        ttl <- valkey.ttl("cl-getex")
        _ <- valkey.del("cl-getex")
      } yield {
        assertEquals(result, Ok(Some("value")))
        val Ok(t) = ttl: @unchecked
        assert(t > 0L && t <= 60L)
      }
    }
  }

  test("GETDEL should return value and delete key on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-getdel", "value")
        result <- valkey.getDel("cl-getdel")
        exists <- valkey.exists("cl-getdel")
      } yield {
        assertEquals(result, Ok(Some("value")))
        assertEquals(exists, Ok(false))
      }
    }
  }

  test("SETNX should set only if not exists on cluster") {
    clusterClient.use { valkey =>
      for {
        first <- valkey.setNx("cl-setnx", "value1")
        second <- valkey.setNx("cl-setnx", "value2")
        result <- valkey.get("cl-setnx")
        _ <- valkey.del("cl-setnx")
      } yield {
        assertEquals(first, Ok(true))
        assertEquals(second, Ok(false))
        assertEquals(result, Ok(Some("value1")))
      }
    }
  }

  test("LCS should return longest common substring on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("{cl-lcs}-1", "ohmytext")
        _ <- valkey.set("{cl-lcs}-2", "mynewtext")
        result <- valkey.lcs("{cl-lcs}-1", "{cl-lcs}-2")
        _ <- valkey.del("{cl-lcs}-1", "{cl-lcs}-2")
      } yield assertEquals(result, Ok("mytext"))
    }
  }

  test("LCSLEN should return length on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("{cl-lcslen}-1", "ohmytext")
        _ <- valkey.set("{cl-lcslen}-2", "mynewtext")
        result <- valkey.lcsLen("{cl-lcslen}-1", "{cl-lcslen}-2")
        _ <- valkey.del("{cl-lcslen}-1", "{cl-lcslen}-2")
      } yield assertEquals(result, Ok(6L))
    }
  }

  // ==================== Key Commands ====================

  test("DEL should delete a key on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-del", "value")
        deleted <- valkey.del("cl-del")
        result <- valkey.get("cl-del")
      } yield {
        assertEquals(deleted, Ok(1L))
        assertEquals(result, Ok(None))
      }
    }
  }

  test("DEL should delete multiple same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("{cl-del}-1", "v1")
        _ <- valkey.set("{cl-del}-2", "v2")
        _ <- valkey.set("{cl-del}-3", "v3")
        deleted <- valkey.del("{cl-del}-1", "{cl-del}-2", "{cl-del}-3")
      } yield assertEquals(deleted, Ok(3L))
    }
  }

  test("EXISTS should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-exists", "value")
        exists <- valkey.exists("cl-exists")
        notExists <- valkey.exists("cl-exists-no")
        _ <- valkey.del("cl-exists")
      } yield {
        assertEquals(exists, Ok(true))
        assertEquals(notExists, Ok(false))
      }
    }
  }

  test("EXISTSMANY should count same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("{cl-em}-1", "v1")
        _ <- valkey.set("{cl-em}-2", "v2")
        count <- valkey.existsMany("{cl-em}-1", "{cl-em}-2", "{cl-em}-3")
        _ <- valkey.del("{cl-em}-1", "{cl-em}-2")
      } yield assertEquals(count, Ok(2L))
    }
  }

  test("UNLINK should remove keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-unlink", "value")
        unlinked <- valkey.unlink("cl-unlink")
        exists <- valkey.exists("cl-unlink")
      } yield {
        assertEquals(unlinked, Ok(1L))
        assertEquals(exists, Ok(false))
      }
    }
  }

  test("EXPIRE/TTL should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-expire", "value")
        set <- valkey.expire("cl-expire", 60)
        ttlResult <- valkey.ttl("cl-expire")
        _ <- valkey.del("cl-expire")
      } yield {
        assertEquals(set, Ok(true))
        val Ok(ttl) = ttlResult: @unchecked
        assert(ttl > 0L && ttl <= 60L)
      }
    }
  }

  test("EXPIRE with condition should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-expire-nx", "value")
        first <- valkey.expire(
          "cl-expire-nx",
          100,
          ExpireCondition.OnlyIfNoExpiry
        )
        second <- valkey.expire(
          "cl-expire-nx",
          200,
          ExpireCondition.OnlyIfNoExpiry
        )
        _ <- valkey.del("cl-expire-nx")
      } yield {
        assertEquals(first, Ok(true))
        assertEquals(second, Ok(false))
      }
    }
  }

  test("PEXPIRE/PTTL should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-pexpire", "value")
        set <- valkey.pexpire("cl-pexpire", 60000)
        pttlResult <- valkey.pttl("cl-pexpire")
        _ <- valkey.del("cl-pexpire")
      } yield {
        assertEquals(set, Ok(true))
        val Ok(pttl) = pttlResult: @unchecked
        assert(pttl > 0L && pttl <= 60000L)
      }
    }
  }

  test("EXPIREAT/EXPIRETIME should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-expireat", "value")
        futureTs = System.currentTimeMillis() / 1000 + 120
        set <- valkey.expireAt("cl-expireat", futureTs)
        result <- valkey.expireTime("cl-expireat")
        _ <- valkey.del("cl-expireat")
      } yield {
        assertEquals(set, Ok(true))
        assertEquals(result, Ok(futureTs))
      }
    }
  }

  test("PEXPIREAT/PEXPIRETIME should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-pexpireat", "value")
        futureMs = System.currentTimeMillis() + 120000
        set <- valkey.pexpireAt("cl-pexpireat", futureMs)
        result <- valkey.pexpireTime("cl-pexpireat")
        _ <- valkey.del("cl-pexpireat")
      } yield {
        assertEquals(set, Ok(true))
        assertEquals(result, Ok(futureMs))
      }
    }
  }

  test("PERSIST should remove expiry on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-persist", "value")
        _ <- valkey.expire("cl-persist", 60)
        persisted <- valkey.persist("cl-persist")
        ttl <- valkey.ttl("cl-persist")
        _ <- valkey.del("cl-persist")
      } yield {
        assertEquals(persisted, Ok(true))
        assertEquals(ttl, Ok(-1L))
      }
    }
  }

  test("TTL edge cases on cluster") {
    clusterClient.use { valkey =>
      for {
        noKey <- valkey.ttl("cl-ttl-nokey")
        _ <- valkey.set("cl-ttl-noexp", "value")
        noExpiry <- valkey.ttl("cl-ttl-noexp")
        _ <- valkey.del("cl-ttl-noexp")
      } yield {
        assertEquals(noKey, Ok(-2L))
        assertEquals(noExpiry, Ok(-1L))
      }
    }
  }

  test("RENAME should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("{cl-rename}-src", "value")
        _ <- valkey.rename("{cl-rename}-src", "{cl-rename}-dst")
        srcExists <- valkey.exists("{cl-rename}-src")
        dstValue <- valkey.get("{cl-rename}-dst")
        _ <- valkey.del("{cl-rename}-dst")
      } yield {
        assertEquals(srcExists, Ok(false))
        assertEquals(dstValue, Ok(Some("value")))
      }
    }
  }

  test("RENAMENX should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("{cl-renamenx}-src", "value")
        renamed <- valkey.renameNx("{cl-renamenx}-src", "{cl-renamenx}-dst")
        dstValue <- valkey.get("{cl-renamenx}-dst")
        _ <- valkey.del("{cl-renamenx}-dst")
      } yield {
        assertEquals(renamed, Ok(true))
        assertEquals(dstValue, Ok(Some("value")))
      }
    }
  }

  test("TYPE should return correct types on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-type-str", "value")
        _ <- valkey.lpush("cl-type-list", "a")
        _ <- valkey.sadd("cl-type-set", "a")
        _ <- valkey.zadd("cl-type-zset", Map("a" -> 1.0))
        _ <- valkey.hset("cl-type-hash", Map("f" -> "v"))
        tStr <- valkey.typeOf("cl-type-str")
        tList <- valkey.typeOf("cl-type-list")
        tSet <- valkey.typeOf("cl-type-set")
        tZset <- valkey.typeOf("cl-type-zset")
        tHash <- valkey.typeOf("cl-type-hash")
        tNone <- valkey.typeOf("cl-type-nokey")
        _ <- valkey.del("cl-type-str")
        _ <- valkey.del("cl-type-list")
        _ <- valkey.del("cl-type-set")
        _ <- valkey.del("cl-type-zset")
        _ <- valkey.del("cl-type-hash")
      } yield {
        assertEquals(tStr, Ok("string"))
        assertEquals(tList, Ok("list"))
        assertEquals(tSet, Ok("set"))
        assertEquals(tZset, Ok("zset"))
        assertEquals(tHash, Ok("hash"))
        assertEquals(tNone, Ok("none"))
      }
    }
  }

  test("OBJECT ENCODING should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-enc", "hello")
        enc <- valkey.objectEncoding("cl-enc")
        _ <- valkey.del("cl-enc")
      } yield {
        val Ok(Some(encoding)) = enc: @unchecked
        assert(encoding == "embstr" || encoding == "raw")
      }
    }
  }

  test("TOUCH should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-touch", "value")
        touched <- valkey.touch("cl-touch")
        _ <- valkey.del("cl-touch")
      } yield assertEquals(touched, Ok(1L))
    }
  }

  test("COPY should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("{cl-copy}-src", "value")
        copied <- valkey.copy("{cl-copy}-src", "{cl-copy}-dst")
        srcVal <- valkey.get("{cl-copy}-src")
        dstVal <- valkey.get("{cl-copy}-dst")
        _ <- valkey.del("{cl-copy}-src", "{cl-copy}-dst")
      } yield {
        assertEquals(copied, Ok(true))
        assertEquals(srcVal, Ok(Some("value")))
        assertEquals(dstVal, Ok(Some("value")))
      }
    }
  }

  test("DUMP/RESTORE should round-trip on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-dump", "hello-world")
        dumped <- valkey.dump("cl-dump")
        _ <- valkey.del("cl-dump")
        _ <- {
          val Ok(Some(bytes)) = dumped: @unchecked
          valkey.restore("cl-dump", 0L, bytes)
        }
        restored <- valkey.get("cl-dump")
        _ <- valkey.del("cl-dump")
      } yield assertEquals(restored, Ok(Some("hello-world")))
    }
  }

  test("SORT should sort a list on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-sort", "3", "1", "2", "5", "4")
        sorted <- valkey.sort("cl-sort")
        _ <- valkey.del("cl-sort")
      } yield assertEquals(sorted, Ok(List("1", "2", "3", "4", "5")))
    }
  }

  test("SORT_RO should sort without modifying on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-sortro", "3", "1", "2")
        sorted <- valkey.sortReadOnly("cl-sortro")
        original <- valkey.lrange("cl-sortro", 0, -1)
        _ <- valkey.del("cl-sortro")
      } yield {
        assertEquals(sorted, Ok(List("1", "2", "3")))
        assertEquals(original, Ok(List("3", "1", "2")))
      }
    }
  }

  test("SORT STORE should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("{cl-ss}-src", "3", "1", "2")
        count <- valkey.sortStore("{cl-ss}-src", "{cl-ss}-dst")
        result <- valkey.lrange("{cl-ss}-dst", 0, -1)
        _ <- valkey.del("{cl-ss}-src", "{cl-ss}-dst")
      } yield {
        assertEquals(count, Ok(3L))
        assertEquals(result, Ok(List("1", "2", "3")))
      }
    }
  }

  test("expire/persist/ttl lifecycle on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-lifecycle", "value")
        ttl1 <- valkey.ttl("cl-lifecycle")
        _ <- valkey.expire("cl-lifecycle", 120)
        ttl2 <- valkey.ttl("cl-lifecycle")
        _ <- valkey.persist("cl-lifecycle")
        ttl3 <- valkey.ttl("cl-lifecycle")
        _ <- valkey.del("cl-lifecycle")
      } yield {
        assertEquals(ttl1, Ok(-1L))
        val Ok(t2) = ttl2: @unchecked
        assert(t2 > 0L && t2 <= 120L)
        assertEquals(ttl3, Ok(-1L))
      }
    }
  }

  // ==================== Hash Commands ====================

  test("Hash commands should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.hset("cl-hash-1", Map("f1" -> "v1", "f2" -> "v2"))
        v <- valkey.hget("cl-hash-1", "f1")
        all <- valkey.hgetall("cl-hash-1")
        _ <- valkey.del("cl-hash-1")
      } yield {
        val Ok(Some(f1)) = v: @unchecked
        assertEquals(f1, "v1")
        val Ok(m) = all: @unchecked
        assertEquals(m.size, 2)
      }
    }
  }

  test("HDEL should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.hset(
          "cl-hdel",
          Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3")
        )
        deleted <- valkey.hdel("cl-hdel", "f1", "f3")
        remaining <- valkey.hgetall("cl-hdel")
        _ <- valkey.del("cl-hdel")
      } yield {
        assertEquals(deleted, Ok(2L))
        val Ok(m) = remaining: @unchecked
        assertEquals(m.size, 1)
        assertEquals(m("f2"), "v2")
      }
    }
  }

  test("HEXISTS should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.hset("cl-hexists", Map("f1" -> "v1"))
        exists <- valkey.hexists("cl-hexists", "f1")
        notExists <- valkey.hexists("cl-hexists", "f2")
        _ <- valkey.del("cl-hexists")
      } yield {
        assertEquals(exists, Ok(true))
        assertEquals(notExists, Ok(false))
      }
    }
  }

  test("HLEN should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.hset(
          "cl-hlen",
          Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3")
        )
        length <- valkey.hlen("cl-hlen")
        _ <- valkey.del("cl-hlen")
      } yield assertEquals(length, Ok(3L))
    }
  }

  test("HINCRBY/HINCRBYFLOAT should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.hset("cl-hincr", Map("count" -> "10", "score" -> "1.5"))
        intResult <- valkey.hincrBy("cl-hincr", "count", 5)
        floatResult <- valkey.hincrByFloat("cl-hincr", "score", 2.5)
        _ <- valkey.del("cl-hincr")
      } yield {
        assertEquals(intResult, Ok(15L))
        assertEquals(floatResult, Ok(4.0))
      }
    }
  }

  test("HKEYS/HVALS should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.hset("cl-hkv", Map("f1" -> "v1", "f2" -> "v2"))
        keys <- valkey.hkeys("cl-hkv")
        vals <- valkey.hvals("cl-hkv")
        _ <- valkey.del("cl-hkv")
      } yield {
        val Ok(k) = keys: @unchecked
        val Ok(v) = vals: @unchecked
        assertEquals(k.toSet, Set("f1", "f2"))
        assertEquals(v.toSet, Set("v1", "v2"))
      }
    }
  }

  test("HSETNX should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.hset("cl-hsetnx", Map("f1" -> "v1"))
        first <- valkey.hsetnx("cl-hsetnx", "f2", "v2")
        second <- valkey.hsetnx("cl-hsetnx", "f1", "new")
        v <- valkey.hget("cl-hsetnx", "f1")
        _ <- valkey.del("cl-hsetnx")
      } yield {
        assertEquals(first, Ok(true))
        assertEquals(second, Ok(false))
        assertEquals(v, Ok(Some("v1")))
      }
    }
  }

  test("HMGET should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.hset("cl-hmget", Map("f1" -> "v1", "f2" -> "v2"))
        result <- valkey.hmget("cl-hmget", "f1", "f2", "f3")
        _ <- valkey.del("cl-hmget")
      } yield {
        val Ok(values) = result: @unchecked
        assertEquals(values, List(Some("v1"), Some("v2"), None))
      }
    }
  }

  test("HSTRLEN should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.hset("cl-hstrlen", Map("name" -> "hello"))
        length <- valkey.hstrlen("cl-hstrlen", "name")
        _ <- valkey.del("cl-hstrlen")
      } yield assertEquals(length, Ok(5L))
    }
  }

  test("HRANDFIELD should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.hset(
          "cl-hrand",
          Map("f1" -> "v1", "f2" -> "v2", "f3" -> "v3")
        )
        field <- valkey.hrandfield("cl-hrand")
        fields <- valkey.hrandfieldWithCount("cl-hrand", 2)
        _ <- valkey.del("cl-hrand")
      } yield {
        val Ok(Some(f)) = field: @unchecked
        assert(Set("f1", "f2", "f3").contains(f))
        val Ok(fs) = fields: @unchecked
        assertEquals(fs.length, 2)
      }
    }
  }

  test("HSCAN should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.hset("cl-hscan", Map("f1" -> "v1", "f2" -> "v2"))
        result <- valkey.hscan("cl-hscan", "0")
        _ <- valkey.del("cl-hscan")
      } yield {
        val Ok(r) = result: @unchecked
        assertEquals(r.values.size, 2)
      }
    }
  }

  // ==================== List Commands ====================

  test("List commands should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-list-1", "a", "b", "c")
        len <- valkey.llen("cl-list-1")
        head <- valkey.lindex("cl-list-1", 0)
        _ <- valkey.del("cl-list-1")
      } yield {
        assertEquals(len, Ok(3L))
        val Ok(Some(h)) = head: @unchecked
        assertEquals(h, "a")
      }
    }
  }

  test("LPUSH/LPOP should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.lpush("cl-lpush", "c", "b", "a")
        popped <- valkey.lpop("cl-lpush")
        _ <- valkey.del("cl-lpush")
      } yield assertEquals(popped, Ok(Some("a")))
    }
  }

  test("RPUSH/RPOP should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-rpush", "a", "b", "c")
        popped <- valkey.rpop("cl-rpush")
        _ <- valkey.del("cl-rpush")
      } yield assertEquals(popped, Ok(Some("c")))
    }
  }

  test("LPOPCOUNT/RPOPCOUNT should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-popcount", "a", "b", "c", "d", "e")
        left <- valkey.lpopCount("cl-popcount", 2)
        right <- valkey.rpopCount("cl-popcount", 2)
        _ <- valkey.del("cl-popcount")
      } yield {
        assertEquals(left, Ok(List("a", "b")))
        assertEquals(right, Ok(List("e", "d")))
      }
    }
  }

  test("LRANGE should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-lrange", "a", "b", "c", "d", "e")
        all <- valkey.lrange("cl-lrange", 0, -1)
        sub <- valkey.lrange("cl-lrange", 1, 3)
        _ <- valkey.del("cl-lrange")
      } yield {
        assertEquals(all, Ok(List("a", "b", "c", "d", "e")))
        assertEquals(sub, Ok(List("b", "c", "d")))
      }
    }
  }

  test("LTRIM should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-ltrim", "a", "b", "c", "d", "e")
        _ <- valkey.ltrim("cl-ltrim", 1, 3)
        result <- valkey.lrange("cl-ltrim", 0, -1)
        _ <- valkey.del("cl-ltrim")
      } yield assertEquals(result, Ok(List("b", "c", "d")))
    }
  }

  test("LSET should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-lset", "a", "b", "c")
        _ <- valkey.lset("cl-lset", 1, "B")
        result <- valkey.lrange("cl-lset", 0, -1)
        _ <- valkey.del("cl-lset")
      } yield assertEquals(result, Ok(List("a", "B", "c")))
    }
  }

  test("LREM should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-lrem", "a", "b", "a", "c", "a")
        count <- valkey.lrem("cl-lrem", 2, "a")
        result <- valkey.lrange("cl-lrem", 0, -1)
        _ <- valkey.del("cl-lrem")
      } yield {
        assertEquals(count, Ok(2L))
        assertEquals(result, Ok(List("b", "c", "a")))
      }
    }
  }

  test("LINSERT should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-linsert", "a", "c")
        result <- valkey.linsert("cl-linsert", InsertPosition.Before, "c", "b")
        items <- valkey.lrange("cl-linsert", 0, -1)
        _ <- valkey.del("cl-linsert")
      } yield {
        assertEquals(result, Ok(InsertResult.Inserted(3L)))
        assertEquals(items, Ok(List("a", "b", "c")))
      }
    }
  }

  test("LPOS should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-lpos", "a", "b", "c", "b", "d")
        index <- valkey.lpos("cl-lpos", "b")
        _ <- valkey.del("cl-lpos")
      } yield assertEquals(index, Ok(Some(1L)))
    }
  }

  test("LPUSHX/RPUSHX should work on cluster") {
    clusterClient.use { valkey =>
      for {
        noKey <- valkey.lpushx("cl-pushx-nokey", "a")
        _ <- valkey.rpush("cl-pushx", "a")
        lp <- valkey.lpushx("cl-pushx", "b")
        rp <- valkey.rpushx("cl-pushx", "c")
        _ <- valkey.del("cl-pushx")
      } yield {
        assertEquals(noKey, Ok(0L))
        assertEquals(lp, Ok(2L))
        assertEquals(rp, Ok(3L))
      }
    }
  }

  test("LMOVE should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("{cl-lmove}-src", "a", "b", "c")
        moved <- valkey.lmove(
          "{cl-lmove}-src",
          "{cl-lmove}-dst",
          ListDirection.Left,
          ListDirection.Right
        )
        _ <- valkey.del("{cl-lmove}-src", "{cl-lmove}-dst")
      } yield assertEquals(moved, Ok(Some("a")))
    }
  }

  test("BLPOP should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-blpop", "a", "b")
        result <- valkey.blpop(List("cl-blpop"), 1.0)
        _ <- valkey.del("cl-blpop")
      } yield {
        val Ok(Some((key, value))) = result: @unchecked
        assertEquals(key, "cl-blpop")
        assertEquals(value, "a")
      }
    }
  }

  test("BRPOP should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-brpop", "a", "b")
        result <- valkey.brpop(List("cl-brpop"), 1.0)
        _ <- valkey.del("cl-brpop")
      } yield {
        val Ok(Some((key, value))) = result: @unchecked
        assertEquals(key, "cl-brpop")
        assertEquals(value, "b")
      }
    }
  }

  test("LMPOP should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.rpush("cl-lmpop", "a", "b", "c")
        result <- valkey.lmpop(List("cl-lmpop"), ListDirection.Left, 2)
        _ <- valkey.del("cl-lmpop")
      } yield {
        val Ok(Some((key, elements))) = result: @unchecked
        assertEquals(key, "cl-lmpop")
        assertEquals(elements, List("a", "b"))
      }
    }
  }

  // ==================== Set Commands ====================

  test("Set commands should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("cl-set-1", "x", "y", "z")
        card <- valkey.scard("cl-set-1")
        isMember <- valkey.sismember("cl-set-1", "y")
        _ <- valkey.del("cl-set-1")
      } yield {
        assertEquals(card, Ok(3L))
        assertEquals(isMember, Ok(true))
      }
    }
  }

  test("SMEMBERS should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("cl-smembers", "a", "b", "c")
        members <- valkey.smembers("cl-smembers")
        _ <- valkey.del("cl-smembers")
      } yield assertEquals(members, Ok(Set("a", "b", "c")))
    }
  }

  test("SREM should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("cl-srem", "a", "b", "c", "d")
        count <- valkey.srem("cl-srem", "b", "d")
        members <- valkey.smembers("cl-srem")
        _ <- valkey.del("cl-srem")
      } yield {
        assertEquals(count, Ok(2L))
        assertEquals(members, Ok(Set("a", "c")))
      }
    }
  }

  test("SMISMEMBER should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("cl-smis", "a", "b", "c")
        results <- valkey.smismember("cl-smis", "a", "x", "c")
        _ <- valkey.del("cl-smis")
      } yield assertEquals(results, Ok(List(true, false, true)))
    }
  }

  test("SPOP/SPOPCOUNT should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("cl-spop", "a", "b", "c", "d", "e")
        popped <- valkey.spop("cl-spop")
        poppedMany <- valkey.spopCount("cl-spop", 2)
        _ <- valkey.del("cl-spop")
      } yield {
        val Ok(p) = popped: @unchecked
        assert(p.isDefined)
        val Ok(pm) = poppedMany: @unchecked
        assertEquals(pm.size, 2)
      }
    }
  }

  test("SRANDMEMBER should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("cl-srand", "a", "b", "c")
        member <- valkey.srandmember("cl-srand")
        _ <- valkey.del("cl-srand")
      } yield {
        val Ok(m) = member: @unchecked
        assert(m.isDefined)
      }
    }
  }

  test("SUNION should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("{cl-su}-1", "a", "b")
        _ <- valkey.sadd("{cl-su}-2", "b", "c")
        union <- valkey.sunion("{cl-su}-1", "{cl-su}-2")
        _ <- valkey.del("{cl-su}-1", "{cl-su}-2")
      } yield assertEquals(union, Ok(Set("a", "b", "c")))
    }
  }

  test("SINTER should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("{cl-si}-1", "a", "b", "c")
        _ <- valkey.sadd("{cl-si}-2", "b", "c", "d")
        inter <- valkey.sinter("{cl-si}-1", "{cl-si}-2")
        _ <- valkey.del("{cl-si}-1", "{cl-si}-2")
      } yield assertEquals(inter, Ok(Set("b", "c")))
    }
  }

  test("SDIFF should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("{cl-sd}-1", "a", "b", "c")
        _ <- valkey.sadd("{cl-sd}-2", "b", "c")
        diff <- valkey.sdiff("{cl-sd}-1", "{cl-sd}-2")
        _ <- valkey.del("{cl-sd}-1", "{cl-sd}-2")
      } yield assertEquals(diff, Ok(Set("a")))
    }
  }

  test("SUNIONSTORE should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("{cl-sus}-1", "a", "b")
        _ <- valkey.sadd("{cl-sus}-2", "b", "c")
        count <- valkey.sunionstore("{cl-sus}-dst", "{cl-sus}-1", "{cl-sus}-2")
        _ <- valkey.del("{cl-sus}-1", "{cl-sus}-2", "{cl-sus}-dst")
      } yield assertEquals(count, Ok(3L))
    }
  }

  test("SINTERSTORE should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("{cl-sis}-1", "a", "b", "c")
        _ <- valkey.sadd("{cl-sis}-2", "b", "c", "d")
        count <- valkey.sinterstore("{cl-sis}-dst", "{cl-sis}-1", "{cl-sis}-2")
        _ <- valkey.del("{cl-sis}-1", "{cl-sis}-2", "{cl-sis}-dst")
      } yield assertEquals(count, Ok(2L))
    }
  }

  test("SDIFFSTORE should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("{cl-sds}-1", "a", "b", "c")
        _ <- valkey.sadd("{cl-sds}-2", "b", "c")
        count <- valkey.sdiffstore("{cl-sds}-dst", "{cl-sds}-1", "{cl-sds}-2")
        _ <- valkey.del("{cl-sds}-1", "{cl-sds}-2", "{cl-sds}-dst")
      } yield assertEquals(count, Ok(1L))
    }
  }

  test("SINTERCARD should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("{cl-sic}-1", "a", "b", "c")
        _ <- valkey.sadd("{cl-sic}-2", "b", "c", "d")
        count <- valkey.sintercard("{cl-sic}-1", "{cl-sic}-2")
        _ <- valkey.del("{cl-sic}-1", "{cl-sic}-2")
      } yield assertEquals(count, Ok(2L))
    }
  }

  test("SMOVE should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("{cl-smove}-1", "a", "b")
        _ <- valkey.sadd("{cl-smove}-2", "x")
        moved <- valkey.smove("{cl-smove}-1", "{cl-smove}-2", "b")
        _ <- valkey.del("{cl-smove}-1", "{cl-smove}-2")
      } yield assertEquals(moved, Ok(true))
    }
  }

  test("SSCAN should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.sadd("cl-sscan", "a", "b", "c", "d")
        result <- valkey.sscan("cl-sscan", "0")
        _ <- valkey.del("cl-sscan")
      } yield {
        val Ok(r) = result: @unchecked
        assertEquals(r.values.size, 4)
      }
    }
  }

  // ==================== Sorted Set Commands ====================

  test("ZADD/ZSCORE/ZRANGE should work on cluster") {
    clusterClient.use { valkey =>
      for {
        count <- valkey.zadd("cl-zset", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        score <- valkey.zscore("cl-zset", "b")
        members <- valkey.zrange("cl-zset", 0, -1)
        _ <- valkey.del("cl-zset")
      } yield {
        assertEquals(count, Ok(3L))
        assertEquals(score, Ok(Some(2.0)))
        assertEquals(members, Ok(List("a", "b", "c")))
      }
    }
  }

  test("ZADD INCR should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zai", Map("a" -> 10.0))
        result <- valkey.zaddIncr("cl-zai", "a", 5.0)
        _ <- valkey.del("cl-zai")
      } yield assertEquals(result, Ok(Some(15.0)))
    }
  }

  test("ZREM should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zrem", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        count <- valkey.zrem("cl-zrem", "b")
        members <- valkey.zrange("cl-zrem", 0, -1)
        _ <- valkey.del("cl-zrem")
      } yield {
        assertEquals(count, Ok(1L))
        assertEquals(members, Ok(List("a", "c")))
      }
    }
  }

  test("ZCARD should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zcard", Map("a" -> 1.0, "b" -> 2.0))
        card <- valkey.zcard("cl-zcard")
        _ <- valkey.del("cl-zcard")
      } yield assertEquals(card, Ok(2L))
    }
  }

  test("ZRANK/ZREVRANK should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zrank", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        rank <- valkey.zrank("cl-zrank", "b")
        revrank <- valkey.zrevrank("cl-zrank", "b")
        _ <- valkey.del("cl-zrank")
      } yield {
        assertEquals(rank, Ok(Some(1L)))
        assertEquals(revrank, Ok(Some(1L)))
      }
    }
  }

  test("ZINCRBY should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zincrby", Map("a" -> 100.0))
        newScore <- valkey.zincrby("cl-zincrby", 50.0, "a")
        _ <- valkey.del("cl-zincrby")
      } yield assertEquals(newScore, Ok(150.0))
    }
  }

  test("ZCOUNT should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "cl-zcount",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        count <- valkey.zcount("cl-zcount", 2.0, 3.0)
        _ <- valkey.del("cl-zcount")
      } yield assertEquals(count, Ok(2L))
    }
  }

  test("ZRANGEWITHSCORES should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zrws", Map("a" -> 1.5, "b" -> 2.5))
        result <- valkey.zrangeWithScores("cl-zrws", 0, -1)
        _ <- valkey.del("cl-zrws")
      } yield assertEquals(result, Ok(List(("a", 1.5), ("b", 2.5))))
    }
  }

  test("ZMSCORE should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zms", Map("a" -> 1.0, "b" -> 2.0))
        scores <- valkey.zmscore("cl-zms", "a", "x", "b")
        _ <- valkey.del("cl-zms")
      } yield assertEquals(scores, Ok(List(Some(1.0), None, Some(2.0))))
    }
  }

  test("ZPOPMIN/ZPOPMAX should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zpop", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        min <- valkey.zpopmin("cl-zpop")
        max <- valkey.zpopmax("cl-zpop")
        _ <- valkey.del("cl-zpop")
      } yield {
        assertEquals(min, Ok(Some(("a", 1.0))))
        assertEquals(max, Ok(Some(("c", 3.0))))
      }
    }
  }

  test("ZRANDMEMBER should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zrand", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        member <- valkey.zrandmember("cl-zrand")
        _ <- valkey.del("cl-zrand")
      } yield {
        val Ok(m) = member: @unchecked
        assert(m.isDefined)
      }
    }
  }

  test("ZDIFF should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("{cl-zd}-1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        _ <- valkey.zadd("{cl-zd}-2", Map("b" -> 2.0, "c" -> 3.0))
        diff <- valkey.zdiff("{cl-zd}-1", "{cl-zd}-2")
        _ <- valkey.del("{cl-zd}-1", "{cl-zd}-2")
      } yield assertEquals(diff, Ok(List("a")))
    }
  }

  test("ZUNION should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("{cl-zu}-1", Map("a" -> 1.0, "b" -> 2.0))
        _ <- valkey.zadd("{cl-zu}-2", Map("b" -> 3.0, "c" -> 4.0))
        union <- valkey.zunion("{cl-zu}-1", "{cl-zu}-2")
        _ <- valkey.del("{cl-zu}-1", "{cl-zu}-2")
      } yield {
        val Ok(members) = union: @unchecked
        assertEquals(members.toSet, Set("a", "b", "c"))
      }
    }
  }

  test("ZINTER should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("{cl-zi}-1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        _ <- valkey.zadd("{cl-zi}-2", Map("b" -> 5.0, "c" -> 6.0))
        inter <- valkey.zinter("{cl-zi}-1", "{cl-zi}-2")
        _ <- valkey.del("{cl-zi}-1", "{cl-zi}-2")
      } yield {
        val Ok(members) = inter: @unchecked
        assertEquals(members.toSet, Set("b", "c"))
      }
    }
  }

  test("ZUNIONSTORE should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("{cl-zus}-1", Map("a" -> 1.0))
        _ <- valkey.zadd("{cl-zus}-2", Map("b" -> 2.0))
        count <- valkey.zunionstore("{cl-zus}-dst", "{cl-zus}-1", "{cl-zus}-2")
        _ <- valkey.del("{cl-zus}-1", "{cl-zus}-2", "{cl-zus}-dst")
      } yield assertEquals(count, Ok(2L))
    }
  }

  test("ZINTERSTORE should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("{cl-zis}-1", Map("a" -> 1.0, "b" -> 2.0))
        _ <- valkey.zadd("{cl-zis}-2", Map("b" -> 3.0))
        count <- valkey.zinterstore("{cl-zis}-dst", "{cl-zis}-1", "{cl-zis}-2")
        _ <- valkey.del("{cl-zis}-1", "{cl-zis}-2", "{cl-zis}-dst")
      } yield assertEquals(count, Ok(1L))
    }
  }

  test("ZDIFFSTORE should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("{cl-zds}-1", Map("a" -> 1.0, "b" -> 2.0))
        _ <- valkey.zadd("{cl-zds}-2", Map("b" -> 2.0))
        count <- valkey.zdiffstore("{cl-zds}-dst", "{cl-zds}-1", "{cl-zds}-2")
        _ <- valkey.del("{cl-zds}-1", "{cl-zds}-2", "{cl-zds}-dst")
      } yield assertEquals(count, Ok(1L))
    }
  }

  test("ZINTERCARD should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("{cl-zic}-1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        _ <- valkey.zadd("{cl-zic}-2", Map("b" -> 5.0, "c" -> 6.0))
        count <- valkey.zintercard("{cl-zic}-1", "{cl-zic}-2")
        _ <- valkey.del("{cl-zic}-1", "{cl-zic}-2")
      } yield assertEquals(count, Ok(2L))
    }
  }

  test("ZREMRANGEBYRANK should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zrrr", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        removed <- valkey.zremrangebyrank("cl-zrrr", 0, 1)
        remaining <- valkey.zrange("cl-zrrr", 0, -1)
        _ <- valkey.del("cl-zrrr")
      } yield {
        assertEquals(removed, Ok(2L))
        assertEquals(remaining, Ok(List("c")))
      }
    }
  }

  test("ZREMRANGEBYSCORE should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zrrs", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        removed <- valkey.zremrangebyscore(
          "cl-zrrs",
          ScoreBoundary.Score(2.0),
          ScoreBoundary.Score(3.0)
        )
        remaining <- valkey.zrange("cl-zrrs", 0, -1)
        _ <- valkey.del("cl-zrrs")
      } yield {
        assertEquals(removed, Ok(2L))
        assertEquals(remaining, Ok(List("a")))
      }
    }
  }

  test("ZLEXCOUNT should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zlc", Map("a" -> 0.0, "b" -> 0.0, "c" -> 0.0))
        count <- valkey.zlexcount(
          "cl-zlc",
          LexBoundary.Lex("a"),
          LexBoundary.Lex("c")
        )
        _ <- valkey.del("cl-zlc")
      } yield assertEquals(count, Ok(3L))
    }
  }

  test("BZPOPMIN should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-bzpm", Map("a" -> 1.0, "b" -> 2.0))
        result <- valkey.bzpopmin(List("cl-bzpm"), 1.0)
        _ <- valkey.del("cl-bzpm")
      } yield {
        val Ok(Some((key, member, score))) = result: @unchecked
        assertEquals(key, "cl-bzpm")
        assertEquals(member, "a")
        assertEquals(score, 1.0)
      }
    }
  }

  test("BZPOPMAX should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-bzpx", Map("a" -> 1.0, "b" -> 2.0))
        result <- valkey.bzpopmax(List("cl-bzpx"), 1.0)
        _ <- valkey.del("cl-bzpx")
      } yield {
        val Ok(Some((key, member, score))) = result: @unchecked
        assertEquals(key, "cl-bzpx")
        assertEquals(member, "b")
        assertEquals(score, 2.0)
      }
    }
  }

  test("ZMPOP should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zmpop", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        result <- valkey.zmpop(List("cl-zmpop"), ScoreFilter.Min)
        _ <- valkey.del("cl-zmpop")
      } yield {
        val Ok(Some((key, elements))) = result: @unchecked
        assertEquals(key, "cl-zmpop")
        assertEquals(elements.head._1, "a")
      }
    }
  }

  test("ZSCAN should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.zadd("cl-zscan", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        result <- valkey.zscan("cl-zscan", "0")
        _ <- valkey.del("cl-zscan")
      } yield {
        val Ok(r) = result: @unchecked
        assertEquals(r.values.size, 3)
      }
    }
  }

  // ==================== HyperLogLog Commands ====================

  test("PFADD/PFCOUNT should work on cluster") {
    clusterClient.use { valkey =>
      for {
        added <- valkey.pfadd("cl-hll", "a", "b", "c")
        count <- valkey.pfcount("cl-hll")
        _ <- valkey.del("cl-hll")
      } yield {
        assertEquals(added, Ok(true))
        assertEquals(count, Ok(3L))
      }
    }
  }

  test("PFADD should handle duplicates on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.pfadd("cl-hll-dup", "a", "b", "c")
        added <- valkey.pfadd("cl-hll-dup", "a", "b", "c")
        count <- valkey.pfcount("cl-hll-dup")
        _ <- valkey.del("cl-hll-dup")
      } yield {
        assertEquals(added, Ok(false))
        assertEquals(count, Ok(3L))
      }
    }
  }

  test("PFMERGE should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.pfadd("{cl-hll}-1", "a", "b", "c")
        _ <- valkey.pfadd("{cl-hll}-2", "c", "d", "e")
        _ <- valkey.pfmerge("{cl-hll}-dst", "{cl-hll}-1", "{cl-hll}-2")
        count <- valkey.pfcount("{cl-hll}-dst")
        _ <- valkey.del("{cl-hll}-1", "{cl-hll}-2", "{cl-hll}-dst")
      } yield assertEquals(count, Ok(5L))
    }
  }

  // ==================== Bitmap Commands ====================

  test("SETBIT/GETBIT should work on cluster") {
    clusterClient.use { valkey =>
      for {
        original <- valkey.setbit("cl-bm", 7, 1)
        current <- valkey.getbit("cl-bm", 7)
        _ <- valkey.del("cl-bm")
      } yield {
        assertEquals(original, Ok(0L))
        assertEquals(current, Ok(1L))
      }
    }
  }

  test("BITCOUNT should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cl-bc", "foobar")
        count <- valkey.bitcount("cl-bc")
        _ <- valkey.del("cl-bc")
      } yield assertEquals(count, Ok(26L))
    }
  }

  test("BITPOS should work on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.setbit("cl-bp", 10, 1)
        pos <- valkey.bitpos("cl-bp", 1)
        _ <- valkey.del("cl-bp")
      } yield assertEquals(pos, Ok(10L))
    }
  }

  test("BITOP should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.setbit("{cl-bo}-1", 0, 1)
        _ <- valkey.setbit("{cl-bo}-1", 1, 1)
        _ <- valkey.setbit("{cl-bo}-2", 0, 1)
        _ <- valkey.setbit("{cl-bo}-2", 1, 0)
        _ <- valkey.bitop(
          BitwiseOperation.And,
          "{cl-bo}-result",
          "{cl-bo}-1",
          "{cl-bo}-2"
        )
        bit0 <- valkey.getbit("{cl-bo}-result", 0)
        bit1 <- valkey.getbit("{cl-bo}-result", 1)
        _ <- valkey.del("{cl-bo}-1", "{cl-bo}-2", "{cl-bo}-result")
      } yield {
        assertEquals(bit0, Ok(1L))
        assertEquals(bit1, Ok(0L))
      }
    }
  }

  // ==================== Geo Commands ====================

  test("GEOADD/GEODIST should work on cluster") {
    clusterClient.use { valkey =>
      val rome = GeoPosition(12.4964, 41.9028)
      val paris = GeoPosition(2.3522, 48.8566)
      for {
        added <- valkey.geoAdd("cl-geo", Map("Rome" -> rome, "Paris" -> paris))
        dist <- valkey.geoDist("cl-geo", "Rome", "Paris", GeoUnit.Kilometers)
        _ <- valkey.del("cl-geo")
      } yield {
        assertEquals(added, Ok(2L))
        val Ok(Some(d)) = dist: @unchecked
        assert(d > 1000 && d < 1200)
      }
    }
  }

  test("GEOPOS should work on cluster") {
    clusterClient.use { valkey =>
      val rome = GeoPosition(12.4964, 41.9028)
      for {
        _ <- valkey.geoAdd("cl-geopos", Map("Rome" -> rome))
        positions <- valkey.geoPos("cl-geopos", "Rome")
        _ <- valkey.del("cl-geopos")
      } yield {
        val Ok(List(Some(pos))) = positions: @unchecked
        assert(math.abs(pos.longitude - rome.longitude) < 0.001)
      }
    }
  }

  test("GEOHASH should work on cluster") {
    clusterClient.use { valkey =>
      val rome = GeoPosition(12.4964, 41.9028)
      for {
        _ <- valkey.geoAdd("cl-geohash", Map("Rome" -> rome))
        hashes <- valkey.geoHash("cl-geohash", "Rome")
        _ <- valkey.del("cl-geohash")
      } yield {
        val Ok(List(Some(hash))) = hashes: @unchecked
        assert(hash.nonEmpty)
      }
    }
  }

  test("GEOSEARCH should work on cluster") {
    clusterClient.use { valkey =>
      val rome = GeoPosition(12.4964, 41.9028)
      val paris = GeoPosition(2.3522, 48.8566)
      val london = GeoPosition(-0.1278, 51.5074)
      for {
        _ <- valkey.geoAdd(
          "cl-geosearch",
          Map("Rome" -> rome, "Paris" -> paris, "London" -> london)
        )
        results <- valkey.geoSearch(
          "cl-geosearch",
          GeoSearchFrom.FromMember[String]("Paris"),
          GeoSearchBy.ByRadius(600, GeoUnit.Kilometers)
        )
        _ <- valkey.del("cl-geosearch")
      } yield {
        val Ok(members) = results: @unchecked
        assert(members.contains("Paris"))
        assert(members.contains("London"))
        assert(!members.contains("Rome"))
      }
    }
  }

  test("GEOSEARCHSTORE should work on same-slot keys on cluster") {
    clusterClient.use { valkey =>
      val rome = GeoPosition(12.4964, 41.9028)
      val paris = GeoPosition(2.3522, 48.8566)
      for {
        _ <- valkey.geoAdd(
          "{cl-gss}-src",
          Map("Rome" -> rome, "Paris" -> paris)
        )
        count <- valkey.geoSearchStore(
          "{cl-gss}-dst",
          "{cl-gss}-src",
          GeoSearchFrom.FromCoord(GeoPosition(10.0, 48.0)),
          GeoSearchBy.ByRadius(2000, GeoUnit.Kilometers)
        )
        _ <- valkey.del("{cl-gss}-src", "{cl-gss}-dst")
      } yield {
        val Ok(c) = count: @unchecked
        assert(c >= 1)
      }
    }
  }

  // ==================== Server Commands ====================

  test("INFO should work on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.info
      } yield {
        val Ok(info) = result: @unchecked
        assert(info.nonEmpty)
      }
    }
  }

  test("INFO with section should work on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.info(Set(InfoSection.Server))
      } yield {
        val Ok(info) = result: @unchecked
        assert(info.contains("tcp_port"))
      }
    }
  }

  test("TIME should work on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.time
      } yield {
        val Ok(t) = result: @unchecked
        assert(t.unixSeconds > 0)
      }
    }
  }

  test("DBSIZE should work on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.dbSize
      } yield {
        val Ok(size) = result: @unchecked
        assert(size >= 0)
      }
    }
  }

  test("CONFIG GET should work on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.configGet(Set("maxmemory"))
      } yield {
        val Ok(config) = result: @unchecked
        assert(config.contains("maxmemory"))
      }
    }
  }

  test("LOLWUT should work on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.lolwut
      } yield {
        val Ok(art) = result: @unchecked
        assert(art.nonEmpty)
      }
    }
  }

  test("LASTSAVE should work on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.lastSave
      } yield {
        val Ok(ts) = result: @unchecked
        assert(ts >= 0)
      }
    }
  }

  // ==================== Cluster Scan ====================

  test("clusterScan should iterate over keys") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cscan-k1", "v1")
        _ <- valkey.set("cscan-k2", "v2")
        result <- valkey.clusterScan(ClusterScanCursor.initial)
        _ <- valkey.del("cscan-k1")
        _ <- valkey.del("cscan-k2")
      } yield {
        val Ok(r) = result: @unchecked
        assert(r.values.nonEmpty)
      }
    }
  }

  test("clusterScan with pattern should filter keys") {
    clusterClient.use { valkey =>
      for {
        _ <- valkey.set("cscan-pat-a", "v1")
        _ <- valkey.set("cscan-pat-b", "v2")
        _ <- valkey.set("cscan-other", "v3")
        result <- valkey.clusterScan(
          ClusterScanCursor.initial,
          "cscan-pat-*",
          100
        )
        _ <- valkey.del("cscan-pat-a")
        _ <- valkey.del("cscan-pat-b")
        _ <- valkey.del("cscan-other")
      } yield {
        val Ok(r) = result: @unchecked
        assert(r.values.forall(_.startsWith("cscan-pat-")))
      }
    }
  }

  test("clusterScan cursor should report isFinished correctly") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.clusterScan(ClusterScanCursor.initial)
      } yield {
        val Ok(r) = result: @unchecked
        assert(!r.cursor.isFinished || r.values.isEmpty)
      }
    }
  }

  // ==================== Scripting Commands ====================

  test("FCALL should work on cluster") {
    clusterClient.use { valkey =>
      for {
        result <- valkey.dbSize
      } yield {
        val Ok(size) = result: @unchecked
        assert(size >= 0)
      }
    }
  }
}
