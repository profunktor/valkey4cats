package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

class SetCommandsSuite extends ValkeyTestSuite {

  test("SADD should add members to set") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.sadd("set1", "a", "b", "c")
        members <- valkey.smembers("set1")
        _ <- valkey.del("set1")
      } yield {
        assertEquals(count, Ok(3L))
        assertEquals(members, Ok(Set("a", "b", "c")))
      }
    }
  }

  test("SADD should not add duplicate members") {
    valkeyClient.use { valkey =>
      for {
        count1 <- valkey.sadd("set2", "a", "b", "c")
        count2 <- valkey.sadd("set2", "b", "c", "d") // b and c already exist
        members <- valkey.smembers("set2")
        _ <- valkey.del("set2")
      } yield {
        assertEquals(count1, Ok(3L))
        assertEquals(count2, Ok(1L)) // Only d is new
        assertEquals(members, Ok(Set("a", "b", "c", "d")))
      }
    }
  }

  test("SREM should remove members from set") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set3", "a", "b", "c", "d", "e")
        count <- valkey.srem("set3", "b", "d")
        members <- valkey.smembers("set3")
        _ <- valkey.del("set3")
      } yield {
        assertEquals(count, Ok(2L))
        assertEquals(members, Ok(Set("a", "c", "e")))
      }
    }
  }

  test("SREM should return 0 for non-existent members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set4", "a", "b", "c")
        count <- valkey.srem("set4", "x", "y", "z")
        _ <- valkey.del("set4")
      } yield assertEquals(count, Ok(0L))
    }
  }

  test("SMEMBERS should return all members of set") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set5", "apple", "banana", "cherry")
        members <- valkey.smembers("set5")
        _ <- valkey.del("set5")
      } yield {
        assertEquals(members, Ok(Set("apple", "banana", "cherry")))
      }
    }
  }

  test("SMEMBERS should return empty set for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        members <- valkey.smembers("non-existent")
      } yield assertEquals(members, Ok(Set.empty[String]))
    }
  }

  test("SISMEMBER should return true for existing member") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set6", "a", "b", "c")
        isMember <- valkey.sismember("set6", "b")
        _ <- valkey.del("set6")
      } yield assertEquals(isMember, Ok(true))
    }
  }

  test("SISMEMBER should return false for non-existent member") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set7", "a", "b", "c")
        isMember <- valkey.sismember("set7", "x")
        _ <- valkey.del("set7")
      } yield assertEquals(isMember, Ok(false))
    }
  }

  test("SMISMEMBER should check multiple members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set8", "a", "b", "c")
        results <- valkey.smismember("set8", "a", "x", "c", "y")
        _ <- valkey.del("set8")
      } yield {
        assertEquals(results, Ok(List(true, false, true, false)))
      }
    }
  }

  test("SCARD should return cardinality of set") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set9", "a", "b", "c", "d", "e")
        cardinality <- valkey.scard("set9")
        _ <- valkey.del("set9")
      } yield assertEquals(cardinality, Ok(5L))
    }
  }

  test("SCARD should return 0 for non-existent set") {
    valkeyClient.use { valkey =>
      for {
        cardinality <- valkey.scard("non-existent")
      } yield assertEquals(cardinality, Ok(0L))
    }
  }

  test("SUNION should return union of sets") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set10a", "a", "b", "c")
        _ <- valkey.sadd("set10b", "c", "d", "e")
        _ <- valkey.sadd("set10c", "e", "f", "g")
        union <- valkey.sunion("set10a", "set10b", "set10c")
        _ <- valkey.del("set10a", "set10b", "set10c")
      } yield {
        assertEquals(union, Ok(Set("a", "b", "c", "d", "e", "f", "g")))
      }
    }
  }

  test("SUNIONSTORE should store union in destination") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set11a", "a", "b", "c")
        _ <- valkey.sadd("set11b", "c", "d", "e")
        count <- valkey.sunionstore("set11dest", "set11a", "set11b")
        members <- valkey.smembers("set11dest")
        _ <- valkey.del("set11a", "set11b", "set11dest")
      } yield {
        assertEquals(count, Ok(5L))
        assertEquals(members, Ok(Set("a", "b", "c", "d", "e")))
      }
    }
  }

  test("SINTER should return intersection of sets") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set12a", "a", "b", "c", "d")
        _ <- valkey.sadd("set12b", "b", "c", "d", "e")
        _ <- valkey.sadd("set12c", "c", "d", "e", "f")
        intersection <- valkey.sinter("set12a", "set12b", "set12c")
        _ <- valkey.del("set12a", "set12b", "set12c")
      } yield {
        assertEquals(intersection, Ok(Set("c", "d")))
      }
    }
  }

  test("SINTERSTORE should store intersection in destination") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set13a", "a", "b", "c", "d")
        _ <- valkey.sadd("set13b", "b", "c", "d", "e")
        count <- valkey.sinterstore("set13dest", "set13a", "set13b")
        members <- valkey.smembers("set13dest")
        _ <- valkey.del("set13a", "set13b", "set13dest")
      } yield {
        assertEquals(count, Ok(3L))
        assertEquals(members, Ok(Set("b", "c", "d")))
      }
    }
  }

  test("SDIFF should return difference of sets") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set14a", "a", "b", "c", "d", "e")
        _ <- valkey.sadd("set14b", "c", "d")
        _ <- valkey.sadd("set14c", "e")
        diff <- valkey.sdiff("set14a", "set14b", "set14c")
        _ <- valkey.del("set14a", "set14b", "set14c")
      } yield {
        assertEquals(diff, Ok(Set("a", "b")))
      }
    }
  }

  test("SDIFFSTORE should store difference in destination") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set15a", "a", "b", "c", "d", "e")
        _ <- valkey.sadd("set15b", "c", "d", "e")
        count <- valkey.sdiffstore("set15dest", "set15a", "set15b")
        members <- valkey.smembers("set15dest")
        _ <- valkey.del("set15a", "set15b", "set15dest")
      } yield {
        assertEquals(count, Ok(2L))
        assertEquals(members, Ok(Set("a", "b")))
      }
    }
  }

  test("SPOP should remove and return random member") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set16", "a", "b", "c", "d", "e")
        popped <- valkey.spop("set16")
        remaining <- valkey.smembers("set16")
        _ <- valkey.del("set16")
      } yield {
        val Ok(p) = popped: @unchecked
        val Ok(r) = remaining: @unchecked
        assert(p.isDefined)
        assertEquals(r.size, 4)
        assert(!r.contains(p.get))
      }
    }
  }

  test("SPOP should return None for non-existent set") {
    valkeyClient.use { valkey =>
      for {
        popped <- valkey.spop("non-existent")
      } yield assertEquals(popped, Ok(None))
    }
  }

  test("SPOPCOUNT should remove and return multiple random members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set17", "a", "b", "c", "d", "e")
        popped <- valkey.spopCount("set17", 3)
        remaining <- valkey.smembers("set17")
        _ <- valkey.del("set17")
      } yield {
        val Ok(p) = popped: @unchecked
        val Ok(r) = remaining: @unchecked
        assertEquals(p.size, 3)
        assertEquals(r.size, 2)
        assert(p.intersect(r).isEmpty) // No overlap
      }
    }
  }

  test("SRANDMEMBER should return random member without removing") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set18", "a", "b", "c", "d", "e")
        member <- valkey.srandmember("set18")
        card <- valkey.scard("set18")
        _ <- valkey.del("set18")
      } yield {
        val Ok(m) = member: @unchecked
        assert(m.isDefined)
        assertEquals(card, Ok(5L)) // Set size unchanged
      }
    }
  }

  test("SRANDMEMBER should return None for non-existent set") {
    valkeyClient.use { valkey =>
      for {
        member <- valkey.srandmember("non-existent")
      } yield assertEquals(member, Ok(None))
    }
  }

  test("SRANDMEMBERCOUNT should return multiple random members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set19", "a", "b", "c", "d", "e")
        members <- valkey.srandmemberCount("set19", 3)
        card <- valkey.scard("set19")
        _ <- valkey.del("set19")
      } yield {
        val Ok(ms) = members: @unchecked
        assertEquals(ms.length, 3)
        assertEquals(card, Ok(5L)) // Set size unchanged
      }
    }
  }

  test("SMOVE should move member between sets") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set20a", "a", "b", "c")
        _ <- valkey.sadd("set20b", "x", "y", "z")
        moved <- valkey.smove("set20a", "set20b", "b")
        membersA <- valkey.smembers("set20a")
        membersB <- valkey.smembers("set20b")
        _ <- valkey.del("set20a", "set20b")
      } yield {
        assertEquals(moved, Ok(true))
        assertEquals(membersA, Ok(Set("a", "c")))
        assertEquals(membersB, Ok(Set("x", "y", "z", "b")))
      }
    }
  }

  test("SMOVE should return false when member doesn't exist") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("set21a", "a", "b", "c")
        _ <- valkey.sadd("set21b", "x", "y", "z")
        moved <- valkey.smove("set21a", "set21b", "notexists")
        _ <- valkey.del("set21a", "set21b")
      } yield assertEquals(moved, Ok(false))
    }
  }

  test("complex workflow: tag management system") {
    valkeyClient.use { valkey =>
      for {
        // Create tags for different posts
        _ <- valkey.sadd("post:1:tags", "scala", "fp", "cats")
        _ <- valkey.sadd("post:2:tags", "scala", "akka", "distributed")
        _ <- valkey.sadd("post:3:tags", "scala", "fp", "zio")

        // Find posts with "scala" tag (all three)
        hasScala1 <- valkey.sismember("post:1:tags", "scala")
        hasScala2 <- valkey.sismember("post:2:tags", "scala")
        hasScala3 <- valkey.sismember("post:3:tags", "scala")

        // Find common tags across all posts (intersection)
        commonTags <- valkey.sinter("post:1:tags", "post:2:tags", "post:3:tags")

        // Find all unique tags (union)
        allTags <- valkey.sunion("post:1:tags", "post:2:tags", "post:3:tags")

        // Find tags unique to post:1 (difference)
        uniqueToPost1 <- valkey.sdiff(
          "post:1:tags",
          "post:2:tags",
          "post:3:tags"
        )

        // Count tags on post:1
        tagCount <- valkey.scard("post:1:tags")

        // Cleanup
        _ <- valkey.del("post:1:tags", "post:2:tags", "post:3:tags")
      } yield {
        assertEquals(hasScala1, Ok(true))
        assertEquals(hasScala2, Ok(true))
        assertEquals(hasScala3, Ok(true))
        assertEquals(commonTags, Ok(Set("scala")))
        assertEquals(
          allTags,
          Ok(Set("scala", "fp", "cats", "akka", "distributed", "zio"))
        )
        assertEquals(uniqueToPost1, Ok(Set("cats")))
        assertEquals(tagCount, Ok(3L))
      }
    }
  }

  // ==================== SINTERCARD ====================

  test("SINTERCARD should return cardinality of intersection") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("sic1", "a", "b", "c", "d")
        _ <- valkey.sadd("sic2", "b", "c", "d", "e")
        count <- valkey.sintercard("sic1", "sic2")
        _ <- valkey.del("sic1", "sic2")
      } yield assertEquals(count, Ok(3L))
    }
  }

  test("SINTERCARD should return 0 for disjoint sets") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("sic-dis1", "a", "b")
        _ <- valkey.sadd("sic-dis2", "c", "d")
        count <- valkey.sintercard("sic-dis1", "sic-dis2")
        _ <- valkey.del("sic-dis1", "sic-dis2")
      } yield assertEquals(count, Ok(0L))
    }
  }

  test("SINTERCARD with limit should cap the count") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("sic-lim1", "a", "b", "c", "d", "e")
        _ <- valkey.sadd("sic-lim2", "a", "b", "c", "d", "e")
        full <- valkey.sintercard("sic-lim1", "sic-lim2")
        limited <- valkey.sintercard(2L, "sic-lim1", "sic-lim2")
        _ <- valkey.del("sic-lim1", "sic-lim2")
      } yield {
        assertEquals(full, Ok(5L))
        assertEquals(limited, Ok(2L))
      }
    }
  }

  test("SINTERCARD with three sets") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("sic3a", "a", "b", "c")
        _ <- valkey.sadd("sic3b", "b", "c", "d")
        _ <- valkey.sadd("sic3c", "c", "d", "e")
        count <- valkey.sintercard("sic3a", "sic3b", "sic3c")
        _ <- valkey.del("sic3a", "sic3b", "sic3c")
      } yield assertEquals(count, Ok(1L))
    }
  }

  test("SSCAN should iterate over set members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.sadd("sscan-1", "a", "b", "c", "d")
        result <- valkey.sscan("sscan-1", "0")
      } yield {
        val Ok(r) = result: @unchecked
        assertEquals(r.cursor, "0")
        assertEquals(r.values.size, 4)
        assert(r.values.contains("a"))
        assert(r.values.contains("d"))
      }
    }
  }

  test("SSCAN on empty set should return empty") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.sscan("sscan-nonexistent", "0")
      } yield {
        val Ok(r) = result: @unchecked
        assertEquals(r.cursor, "0")
        assert(r.values.isEmpty)
      }
    }
  }
}
