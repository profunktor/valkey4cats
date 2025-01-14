package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.arguments.{
  AggregateOption,
  LexBoundary,
  RangeQuery,
  ScoreBoundary,
  ScoreFilter
}
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

class SortedSetCommandsSuite extends ValkeyTestSuite {

  test("ZADD should add members with scores") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.zadd("zset1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        members <- valkey.zrange("zset1", 0, -1)
        _ <- valkey.del("zset1")
      } yield {
        assertEquals(count, Ok(3L))
        assertEquals(members, Ok(List("a", "b", "c")))
      }
    }
  }

  test("ZADD should update scores for existing members") {
    valkeyClient.use { valkey =>
      for {
        count1 <- valkey.zadd("zset2", Map("a" -> 1.0, "b" -> 2.0))
        count2 <- valkey.zadd("zset2", Map("b" -> 5.0)) // Update b's score
        members <- valkey.zrange("zset2", 0, -1)
        _ <- valkey.del("zset2")
      } yield {
        assertEquals(count1, Ok(2L))
        assertEquals(count2, Ok(0L)) // 0 because b already existed
        assertEquals(members, Ok(List("a", "b"))) // b now has higher score
      }
    }
  }

  test("ZADD INCR should return new score for existing member") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zai1", Map("a" -> 10.0))
        result <- valkey.zaddIncr("zai1", "a", 5.0)
        score <- valkey.zscore("zai1", "a")
        _ <- valkey.del("zai1")
      } yield {
        assertEquals(result, Ok(Some(15.0)))
        assertEquals(score, Ok(Some(15.0)))
      }
    }
  }

  test("ZADD INCR should create member if it doesn't exist") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.zaddIncr("zai2", "new-member", 42.0)
        score <- valkey.zscore("zai2", "new-member")
        _ <- valkey.del("zai2")
      } yield {
        assertEquals(result, Ok(Some(42.0)))
        assertEquals(score, Ok(Some(42.0)))
      }
    }
  }

  test("ZREM should remove members from sorted set") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zset3",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        count <- valkey.zrem("zset3", "b", "d")
        members <- valkey.zrange("zset3", 0, -1)
        _ <- valkey.del("zset3")
      } yield {
        assertEquals(count, Ok(2L))
        assertEquals(members, Ok(List("a", "c")))
      }
    }
  }

  test("ZRANGE should return members in score order") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zset4",
          Map("alice" -> 100.0, "bob" -> 50.0, "charlie" -> 150.0)
        )
        members <- valkey.zrange("zset4", 0, -1)
        _ <- valkey.del("zset4")
      } yield {
        assertEquals(members, Ok(List("bob", "alice", "charlie")))
      }
    }
  }

  test("ZRANGE should return subset by index") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zset5",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0, "e" -> 5.0)
        )
        members <- valkey.zrange("zset5", 1, 3)
        _ <- valkey.del("zset5")
      } yield {
        assertEquals(members, Ok(List("b", "c", "d")))
      }
    }
  }

  test("ZRANGEWITHSCORES should return members with scores") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zset6", Map("a" -> 1.5, "b" -> 2.5, "c" -> 3.5))
        membersWithScores <- valkey.zrangeWithScores("zset6", 0, -1)
        _ <- valkey.del("zset6")
      } yield {
        assertEquals(
          membersWithScores,
          Ok(List(("a", 1.5), ("b", 2.5), ("c", 3.5)))
        )
      }
    }
  }

  test("ZSCORE should return score of member") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zset7", Map("alice" -> 95.5, "bob" -> 87.3))
        score <- valkey.zscore("zset7", "alice")
        _ <- valkey.del("zset7")
      } yield {
        assertEquals(score, Ok(Some(95.5)))
      }
    }
  }

  test("ZSCORE should return None for non-existent member") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zset8", Map("alice" -> 95.5))
        score <- valkey.zscore("zset8", "bob")
        _ <- valkey.del("zset8")
      } yield assertEquals(score, Ok(None))
    }
  }

  test("ZMSCORE should return scores for multiple members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zset9", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        scores <- valkey.zmscore("zset9", "a", "x", "c")
        _ <- valkey.del("zset9")
      } yield {
        assertEquals(scores, Ok(List(Some(1.0), None, Some(3.0))))
      }
    }
  }

  test("ZCARD should return number of members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zset10",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        cardinality <- valkey.zcard("zset10")
        _ <- valkey.del("zset10")
      } yield assertEquals(cardinality, Ok(4L))
    }
  }

  test("ZCARD should return 0 for non-existent sorted set") {
    valkeyClient.use { valkey =>
      for {
        cardinality <- valkey.zcard("non-existent")
      } yield assertEquals(cardinality, Ok(0L))
    }
  }

  test("ZRANK should return rank of member (0-based, ascending)") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zset11",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        rank <- valkey.zrank("zset11", "c")
        _ <- valkey.del("zset11")
      } yield assertEquals(rank, Ok(Some(2L))) // 0: a, 1: b, 2: c
    }
  }

  test("ZRANK should return None for non-existent member") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zset12", Map("a" -> 1.0, "b" -> 2.0))
        rank <- valkey.zrank("zset12", "x")
        _ <- valkey.del("zset12")
      } yield assertEquals(rank, Ok(None))
    }
  }

  test("ZREVRANK should return rank in descending order") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zset13",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        rank <- valkey.zrevrank("zset13", "c")
        _ <- valkey.del("zset13")
      } yield assertEquals(
        rank,
        Ok(Some(1L))
      ) // Reversed: 0: d, 1: c, 2: b, 3: a
    }
  }

  test("ZINCRBY should increment member score") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zset14", Map("player1" -> 100.0))
        newScore <- valkey.zincrby("zset14", 50.0, "player1")
        score <- valkey.zscore("zset14", "player1")
        _ <- valkey.del("zset14")
      } yield {
        assertEquals(newScore, Ok(150.0))
        assertEquals(score, Ok(Some(150.0)))
      }
    }
  }

  test("ZINCRBY should initialize member if doesn't exist") {
    valkeyClient.use { valkey =>
      for {
        newScore <- valkey.zincrby("zset15", 42.5, "new-member")
        _ <- valkey.del("zset15")
      } yield assertEquals(newScore, Ok(42.5))
    }
  }

  test("ZCOUNT should count members in score range") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zset16",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0, "e" -> 5.0)
        )
        count <- valkey.zcount("zset16", 2.0, 4.0)
        _ <- valkey.del("zset16")
      } yield assertEquals(count, Ok(3L)) // b, c, d
    }
  }

  test("ZPOPMIN should remove and return member with lowest score") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zset17", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        popped <- valkey.zpopmin("zset17")
        remaining <- valkey.zrange("zset17", 0, -1)
        _ <- valkey.del("zset17")
      } yield {
        assertEquals(popped, Ok(Some(("a", 1.0))))
        assertEquals(remaining, Ok(List("b", "c")))
      }
    }
  }

  test("ZPOPMIN should return None for non-existent sorted set") {
    valkeyClient.use { valkey =>
      for {
        popped <- valkey.zpopmin("non-existent")
      } yield assertEquals(popped, Ok(None))
    }
  }

  test(
    "ZPOPMINCOUNT should remove and return multiple members with lowest scores"
  ) {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zset18",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        popped <- valkey.zpopminCount("zset18", 2)
        remaining <- valkey.zrange("zset18", 0, -1)
        _ <- valkey.del("zset18")
      } yield {
        assertEquals(popped, Ok(List(("a", 1.0), ("b", 2.0))))
        assertEquals(remaining, Ok(List("c", "d")))
      }
    }
  }

  test("ZPOPMAX should remove and return member with highest score") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zset19", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        popped <- valkey.zpopmax("zset19")
        remaining <- valkey.zrange("zset19", 0, -1)
        _ <- valkey.del("zset19")
      } yield {
        assertEquals(popped, Ok(Some(("c", 3.0))))
        assertEquals(remaining, Ok(List("a", "b")))
      }
    }
  }

  test(
    "ZPOPMAXCOUNT should remove and return multiple members with highest scores"
  ) {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zset20",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        popped <- valkey.zpopmaxCount("zset20", 2)
        remaining <- valkey.zrange("zset20", 0, -1)
        _ <- valkey.del("zset20")
      } yield {
        assertEquals(popped, Ok(List(("d", 4.0), ("c", 3.0))))
        assertEquals(remaining, Ok(List("a", "b")))
      }
    }
  }

  test("ZRANDMEMBER should return random member without removing") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zset21", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        member <- valkey.zrandmember("zset21")
        card <- valkey.zcard("zset21")
        _ <- valkey.del("zset21")
      } yield {
        val Ok(m) = member: @unchecked
        assert(m.isDefined)
        assert(Set("a", "b", "c").contains(m.get))
        assertEquals(card, Ok(3L)) // Size unchanged
      }
    }
  }

  test("ZRANDMEMBER should return None for non-existent sorted set") {
    valkeyClient.use { valkey =>
      for {
        member <- valkey.zrandmember("non-existent")
      } yield assertEquals(member, Ok(None))
    }
  }

  test("ZRANDMEMBERCOUNT should return multiple random members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zset22",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        members <- valkey.zrandmemberCount("zset22", 2)
        card <- valkey.zcard("zset22")
        _ <- valkey.del("zset22")
      } yield {
        val Ok(ms) = members: @unchecked
        assertEquals(ms.length, 2)
        assertEquals(card, Ok(4L)) // Size unchanged
      }
    }
  }

  test("ZRANDMEMBERWITHSCORES should return random members with scores") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zset23", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        membersWithScores <- valkey.zrandmemberWithScores("zset23", 2)
        _ <- valkey.del("zset23")
      } yield {
        val Ok(mws) = membersWithScores: @unchecked
        assertEquals(mws.length, 2)
        mws.foreach { case (member, score) =>
          assert(Set("a", "b", "c").contains(member))
          assert(score >= 1.0 && score <= 3.0)
        }
      }
    }
  }

  test("complex workflow: leaderboard system") {
    valkeyClient.use { valkey =>
      for {
        // Initialize player scores
        _ <- valkey.zadd(
          "leaderboard",
          Map(
            "Alice" -> 1000.0,
            "Bob" -> 1500.0,
            "Charlie" -> 800.0,
            "Diana" -> 1200.0,
            "Eve" -> 900.0
          )
        )

        // Get top 3 players
        top3 <- valkey.zrangeWithScores(
          "leaderboard",
          -3,
          -1
        ) // Last 3 (highest scores)

        // Get Alice's rank (0-based from lowest, so we use zrevrank for highest-first)
        aliceRevRank <- valkey.zrevrank("leaderboard", "Alice")

        // Alice wins a game and gains 300 points
        aliceNewScore <- valkey.zincrby("leaderboard", 300.0, "Alice")

        // Get updated top 3
        updatedTop3 <- valkey.zrangeWithScores("leaderboard", -3, -1)

        // Count players with score >= 1000
        highScorers <- valkey.zcount("leaderboard", 1000.0, Double.MaxValue)

        // Get total player count
        totalPlayers <- valkey.zcard("leaderboard")

        // Cleanup
        _ <- valkey.del("leaderboard")
      } yield {
        val Ok(t3) = top3: @unchecked
        // Original top 3: Bob (1500), Diana (1200), Alice (1000)
        assertEquals(t3.map(_._1), List("Alice", "Diana", "Bob"))

        // Alice was originally 3rd from top (0: Bob, 1: Diana, 2: Alice)
        assertEquals(aliceRevRank, Ok(Some(2L)))

        // Alice's new score
        assertEquals(aliceNewScore, Ok(1300.0))

        val Ok(ut3) = updatedTop3: @unchecked
        // Updated top 3: Bob (1500), Alice (1300), Diana (1200)
        assertEquals(ut3.map(_._1), List("Diana", "Alice", "Bob"))

        // High scorers: Bob, Alice, Diana (now 3 players >= 1000)
        assertEquals(highScorers, Ok(3L))

        // Total players
        assertEquals(totalPlayers, Ok(5L))
      }
    }
  }

  test("complex workflow: priority queue") {
    valkeyClient.use { valkey =>
      for {
        // Add tasks with priorities (lower score = higher priority)
        _ <- valkey.zadd(
          "tasks",
          Map(
            "critical-bug" -> 1.0,
            "feature-request" -> 5.0,
            "documentation" -> 10.0,
            "security-fix" -> 2.0
          )
        )

        // Get highest priority task
        topTask <- valkey.zpopmin("tasks")

        // Add urgent task
        _ <- valkey.zadd("tasks", Map("urgent-patch" -> 1.5))

        // Process next 2 tasks
        nextTasks <- valkey.zpopminCount("tasks", 2)

        // Check remaining tasks
        remaining <- valkey.zrangeWithScores("tasks", 0, -1)

        // Cleanup
        _ <- valkey.del("tasks")
      } yield {
        assertEquals(topTask, Ok(Some(("critical-bug", 1.0))))
        val Ok(nt) = nextTasks: @unchecked
        assertEquals(nt.map(_._1), List("urgent-patch", "security-fix"))
        val Ok(r) = remaining: @unchecked
        assertEquals(
          r.map(_._1),
          List("feature-request", "documentation")
        )
      }
    }
  }

  // ==================== ZREMRANGEBYRANK ====================

  test("ZREMRANGEBYRANK should remove elements by rank range") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zrrr",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        removed <- valkey.zremrangebyrank("zrrr", 0, 1)
        remaining <- valkey.zrange("zrrr", 0, -1)
        _ <- valkey.del("zrrr")
      } yield {
        assertEquals(removed, Ok(2L))
        assertEquals(remaining, Ok(List("c", "d")))
      }
    }
  }

  test("ZREMRANGEBYRANK with negative indices should count from end") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zrrr-neg", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        removed <- valkey.zremrangebyrank("zrrr-neg", -2, -1)
        remaining <- valkey.zrange("zrrr-neg", 0, -1)
        _ <- valkey.del("zrrr-neg")
      } yield {
        assertEquals(removed, Ok(2L))
        assertEquals(remaining, Ok(List("a")))
      }
    }
  }

  // ==================== ZREMRANGEBYSCORE ====================

  test("ZREMRANGEBYSCORE should remove elements by score range") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zrrs",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        removed <- valkey.zremrangebyscore(
          "zrrs",
          ScoreBoundary.Score(2.0),
          ScoreBoundary.Score(3.0)
        )
        remaining <- valkey.zrange("zrrs", 0, -1)
        _ <- valkey.del("zrrs")
      } yield {
        assertEquals(removed, Ok(2L))
        assertEquals(remaining, Ok(List("a", "d")))
      }
    }
  }

  test("ZREMRANGEBYSCORE with infinity should remove all above min") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zrrs-inf",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0)
        )
        removed <- valkey.zremrangebyscore(
          "zrrs-inf",
          ScoreBoundary.Score(2.0),
          ScoreBoundary.PositiveInfinity
        )
        remaining <- valkey.zrange("zrrs-inf", 0, -1)
        _ <- valkey.del("zrrs-inf")
      } yield {
        assertEquals(removed, Ok(2L))
        assertEquals(remaining, Ok(List("a")))
      }
    }
  }

  // ==================== ZDIFF / ZDIFFSTORE ====================

  test("ZDIFF should return difference between sorted sets") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zdiff1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        _ <- valkey.zadd("zdiff2", Map("b" -> 2.0, "c" -> 3.0, "d" -> 4.0))
        diff <- valkey.zdiff("zdiff1", "zdiff2")
        _ <- valkey.del("zdiff1", "zdiff2")
      } yield assertEquals(diff, Ok(List("a")))
    }
  }

  test("ZDIFFSTORE should store difference in destination") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zds1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        _ <- valkey.zadd("zds2", Map("b" -> 2.0))
        count <- valkey.zdiffstore("zds-dest", "zds1", "zds2")
        members <- valkey.zrange("zds-dest", 0, -1)
        _ <- valkey.del("zds1", "zds2", "zds-dest")
      } yield {
        assertEquals(count, Ok(2L))
        assertEquals(members, Ok(List("a", "c")))
      }
    }
  }

  // ==================== ZUNION / ZUNIONSTORE ====================

  test("ZUNION should return union of sorted sets") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zu1", Map("a" -> 1.0, "b" -> 2.0))
        _ <- valkey.zadd("zu2", Map("b" -> 3.0, "c" -> 4.0))
        union <- valkey.zunion("zu1", "zu2")
        _ <- valkey.del("zu1", "zu2")
      } yield {
        val Ok(members) = union: @unchecked
        assertEquals(members.toSet, Set("a", "b", "c"))
      }
    }
  }

  test("ZUNIONSTORE should store union in destination") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zus1", Map("a" -> 1.0, "b" -> 2.0))
        _ <- valkey.zadd("zus2", Map("c" -> 3.0))
        count <- valkey.zunionstore("zus-dest", "zus1", "zus2")
        members <- valkey.zrange("zus-dest", 0, -1)
        _ <- valkey.del("zus1", "zus2", "zus-dest")
      } yield {
        assertEquals(count, Ok(3L))
        assertEquals(members, Ok(List("a", "b", "c")))
      }
    }
  }

  // ==================== ZINTER / ZINTERSTORE ====================

  test("ZINTER should return intersection of sorted sets") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zi1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        _ <- valkey.zadd("zi2", Map("b" -> 5.0, "c" -> 6.0, "d" -> 7.0))
        inter <- valkey.zinter("zi1", "zi2")
        _ <- valkey.del("zi1", "zi2")
      } yield {
        val Ok(members) = inter: @unchecked
        assertEquals(members.toSet, Set("b", "c"))
      }
    }
  }

  test("ZINTERSTORE should store intersection in destination") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zis1", Map("a" -> 1.0, "b" -> 2.0))
        _ <- valkey.zadd("zis2", Map("b" -> 3.0, "c" -> 4.0))
        count <- valkey.zinterstore("zis-dest", "zis1", "zis2")
        members <- valkey.zrange("zis-dest", 0, -1)
        _ <- valkey.del("zis1", "zis2", "zis-dest")
      } yield {
        assertEquals(count, Ok(1L))
        assertEquals(members, Ok(List("b")))
      }
    }
  }

  test("ZINTER with disjoint sets should return empty") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zi-dis1", Map("a" -> 1.0))
        _ <- valkey.zadd("zi-dis2", Map("b" -> 2.0))
        inter <- valkey.zinter("zi-dis1", "zi-dis2")
        _ <- valkey.del("zi-dis1", "zi-dis2")
      } yield assertEquals(inter, Ok(List.empty[String]))
    }
  }

  // ==================== ZINTERCARD ====================

  test("ZINTERCARD should return cardinality of intersection") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zic1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        _ <- valkey.zadd("zic2", Map("b" -> 5.0, "c" -> 6.0, "d" -> 7.0))
        count <- valkey.zintercard("zic1", "zic2")
        _ <- valkey.del("zic1", "zic2")
      } yield assertEquals(count, Ok(2L))
    }
  }

  test("ZINTERCARD with limit should cap the count") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zic-lim1",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        _ <- valkey.zadd(
          "zic-lim2",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        full <- valkey.zintercard("zic-lim1", "zic-lim2")
        limited <- valkey.zintercard(2L, "zic-lim1", "zic-lim2")
        _ <- valkey.del("zic-lim1", "zic-lim2")
      } yield {
        assertEquals(full, Ok(4L))
        assertEquals(limited, Ok(2L))
      }
    }
  }

  test("ZINTERCARD with disjoint sets should return 0") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zic-dis1", Map("a" -> 1.0))
        _ <- valkey.zadd("zic-dis2", Map("b" -> 2.0))
        count <- valkey.zintercard("zic-dis1", "zic-dis2")
        _ <- valkey.del("zic-dis1", "zic-dis2")
      } yield assertEquals(count, Ok(0L))
    }
  }

  // ==================== ZRANKWITHSCORE / ZREVRANKWITHSCORE ====================

  test("ZRANKWITHSCORE should return rank and score") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zrws",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        result <- valkey.zrankWithScore("zrws", "c")
        _ <- valkey.del("zrws")
      } yield {
        assertEquals(result, Ok(Some((2L, 3.0))))
      }
    }
  }

  test("ZRANKWITHSCORE should return None for non-existent member") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zrws2", Map("a" -> 1.0))
        result <- valkey.zrankWithScore("zrws2", "x")
        _ <- valkey.del("zrws2")
      } yield assertEquals(result, Ok(None))
    }
  }

  test("ZREVRANKWITHSCORE should return rank in descending order and score") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zrrws",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        result <- valkey.zrevrankWithScore("zrrws", "c")
        _ <- valkey.del("zrrws")
      } yield {
        assertEquals(result, Ok(Some((1L, 3.0))))
      }
    }
  }

  // ==================== BZPOPMIN / BZPOPMAX ====================

  test("BZPOPMIN should pop member with lowest score") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("bzpm", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        result <- valkey.bzpopmin(List("bzpm"), 1.0)
        _ <- valkey.del("bzpm")
      } yield {
        val Ok(Some((key, member, score))) = result: @unchecked
        assertEquals(key, "bzpm")
        assertEquals(member, "a")
        assertEquals(score, 1.0)
      }
    }
  }

  test("BZPOPMIN should timeout on empty set") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.bzpopmin(List("bzpm-empty"), 0.1)
      } yield assertEquals(result, Ok(None))
    }
  }

  test("BZPOPMAX should pop member with highest score") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("bzpx", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        result <- valkey.bzpopmax(List("bzpx"), 1.0)
        _ <- valkey.del("bzpx")
      } yield {
        val Ok(Some((key, member, score))) = result: @unchecked
        assertEquals(key, "bzpx")
        assertEquals(member, "c")
        assertEquals(score, 3.0)
      }
    }
  }

  test("BZPOPMAX should timeout on empty set") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.bzpopmax(List("bzpx-empty"), 0.1)
      } yield assertEquals(result, Ok(None))
    }
  }

  test("BZPOPMIN should check multiple keys in order") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("bzpm-second", Map("x" -> 5.0))
        result <- valkey.bzpopmin(List("bzpm-first-empty", "bzpm-second"), 1.0)
        _ <- valkey.del("bzpm-second")
      } yield {
        val Ok(Some((key, member, score))) = result: @unchecked
        assertEquals(key, "bzpm-second")
        assertEquals(member, "x")
        assertEquals(score, 5.0)
      }
    }
  }

  test("ZDIFFWITHSCORES should return difference with scores") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zdws-1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        _ <- valkey.zadd("zdws-2", Map("b" -> 2.0, "c" -> 3.0))
        result <- valkey.zdiffWithScores("zdws-1", "zdws-2")
      } yield {
        val Ok(entries) = result: @unchecked
        assertEquals(entries.size, 1)
        assertEquals(entries.head._2, 1.0)
      }
    }
  }

  test("ZUNIONWITHSCORES should return union with scores") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zuws-1", Map("a" -> 1.0, "b" -> 2.0))
        _ <- valkey.zadd("zuws-2", Map("b" -> 3.0, "c" -> 4.0))
        result <- valkey.zunionWithScores("zuws-1", "zuws-2")
      } yield {
        val Ok(entries) = result: @unchecked
        assertEquals(entries.size, 3)
        val scoreMap = entries.toMap
        assertEquals(scoreMap("a"), 1.0)
        assertEquals(scoreMap("b"), 5.0)
        assertEquals(scoreMap("c"), 4.0)
      }
    }
  }

  test("ZUNIONWITHSCORES with MAX aggregate should use max scores") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zuwsm-1", Map("a" -> 1.0, "b" -> 2.0))
        _ <- valkey.zadd("zuwsm-2", Map("b" -> 3.0, "c" -> 4.0))
        result <- valkey.zunionWithScores(
          List("zuwsm-1", "zuwsm-2"),
          AggregateOption.Max
        )
      } yield {
        val Ok(entries) = result: @unchecked
        val scoreMap = entries.toMap
        assertEquals(scoreMap("b"), 3.0)
      }
    }
  }

  test("ZINTERWITHSCORES should return intersection with scores") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("ziws-1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        _ <- valkey.zadd("ziws-2", Map("b" -> 5.0, "c" -> 6.0, "d" -> 7.0))
        result <- valkey.zinterWithScores("ziws-1", "ziws-2")
      } yield {
        val Ok(entries) = result: @unchecked
        assertEquals(entries.size, 2)
        val scoreMap = entries.toMap
        assertEquals(scoreMap("b"), 7.0)
        assertEquals(scoreMap("c"), 9.0)
      }
    }
  }

  test("ZINTERWITHSCORES with MIN aggregate should use min scores") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("ziwsm-1", Map("a" -> 1.0, "b" -> 2.0))
        _ <- valkey.zadd("ziwsm-2", Map("b" -> 5.0, "c" -> 6.0))
        result <- valkey.zinterWithScores(
          List("ziwsm-1", "ziwsm-2"),
          AggregateOption.Min
        )
      } yield {
        val Ok(entries) = result: @unchecked
        assertEquals(entries.size, 1)
        val scoreMap = entries.toMap
        assertEquals(scoreMap("b"), 2.0)
      }
    }
  }

  test("ZLEXCOUNT should count members in lexicographic range") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zlc-1",
          Map("a" -> 0.0, "b" -> 0.0, "c" -> 0.0, "d" -> 0.0, "e" -> 0.0)
        )
        count <- valkey.zlexcount(
          "zlc-1",
          LexBoundary.Lex("b"),
          LexBoundary.Lex("d")
        )
      } yield assertEquals(count, Ok(3L))
    }
  }

  test("ZLEXCOUNT with infinity bounds should count all members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zlc-2",
          Map("a" -> 0.0, "b" -> 0.0, "c" -> 0.0)
        )
        count <- valkey.zlexcount(
          "zlc-2",
          LexBoundary.NegativeInfinity,
          LexBoundary.PositiveInfinity
        )
      } yield assertEquals(count, Ok(3L))
    }
  }

  test("ZREMRANGEBYLEX should remove members in range") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zrrl-1",
          Map("a" -> 0.0, "b" -> 0.0, "c" -> 0.0, "d" -> 0.0)
        )
        removed <- valkey.zremrangebylex(
          "zrrl-1",
          LexBoundary.Lex("b"),
          LexBoundary.Lex("c")
        )
        remaining <- valkey.zcard("zrrl-1")
      } yield {
        assertEquals(removed, Ok(2L))
        assertEquals(remaining, Ok(2L))
      }
    }
  }

  test("ZRANGESTORE should store range into destination") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zrs-src",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)
        )
        stored <- valkey.zrangestore(
          "zrs-dst",
          "zrs-src",
          RangeQuery.ByIndex(1, 2)
        )
        members <- valkey.zrange("zrs-dst", 0, -1)
      } yield {
        assertEquals(stored, Ok(2L))
        val Ok(ms) = members: @unchecked
        assertEquals(ms, List("b", "c"))
      }
    }
  }

  test("ZRANGESTORE with reverse should store reversed range") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd(
          "zrs-rev-src",
          Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0)
        )
        stored <- valkey.zrangestore(
          "zrs-rev-dst",
          "zrs-rev-src",
          RangeQuery.ByIndex(0, 1),
          reverse = true
        )
        members <- valkey.zrange("zrs-rev-dst", 0, -1)
      } yield {
        assertEquals(stored, Ok(2L))
        val Ok(ms) = members: @unchecked
        assertEquals(ms, List("b", "c"))
      }
    }
  }

  test("ZMPOP MIN should pop the lowest-scored member") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zmpop-1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        result <- valkey.zmpop(List("zmpop-1"), ScoreFilter.Min)
      } yield {
        val Ok(Some((key, elements))) = result: @unchecked
        assertEquals(key, "zmpop-1")
        assertEquals(elements.size, 1)
        assertEquals(elements.head._1, "a")
        assertEquals(elements.head._2, 1.0)
      }
    }
  }

  test("ZMPOP MAX with count should pop multiple highest-scored members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zmpop-2", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        result <- valkey.zmpop(List("zmpop-2"), ScoreFilter.Max, 2)
      } yield {
        val Ok(Some((key, elements))) = result: @unchecked
        assertEquals(key, "zmpop-2")
        assertEquals(elements.size, 2)
      }
    }
  }

  test("ZMPOP from empty sets should return None") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.zmpop(
          List("zmpop-empty-1", "zmpop-empty-2"),
          ScoreFilter.Min
        )
      } yield {
        val Ok(value) = result: @unchecked
        assertEquals(value, None)
      }
    }
  }

  test("BZMPOP should pop from available sorted set") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("bzmpop-1", Map("x" -> 1.0, "y" -> 2.0))
        result <- valkey.bzmpop(
          List("bzmpop-1"),
          ScoreFilter.Min,
          1.0
        )
      } yield {
        val Ok(Some((key, elements))) = result: @unchecked
        assertEquals(key, "bzmpop-1")
        assertEquals(elements.head._1, "x")
      }
    }
  }

  test("BZMPOP with count should pop multiple elements") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("bzmpop-2", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        result <- valkey.bzmpop(
          List("bzmpop-2"),
          ScoreFilter.Max,
          1.0,
          2
        )
      } yield {
        val Ok(Some((key, elements))) = result: @unchecked
        assertEquals(key, "bzmpop-2")
        assertEquals(elements.size, 2)
      }
    }
  }

  test("ZSCAN should iterate over sorted set members with scores") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.zadd("zscan-1", Map("a" -> 1.0, "b" -> 2.0, "c" -> 3.0))
        result <- valkey.zscan("zscan-1", "0")
      } yield {
        val Ok(r) = result: @unchecked
        assertEquals(r.cursor, "0")
        assertEquals(r.values.size, 3)
        val scoreMap = r.values.toMap
        assertEquals(scoreMap("a"), 1.0)
        assertEquals(scoreMap("b"), 2.0)
        assertEquals(scoreMap("c"), 3.0)
      }
    }
  }

  test("ZSCAN on empty sorted set should return empty") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.zscan("zscan-nonexistent", "0")
      } yield {
        val Ok(r) = result: @unchecked
        assertEquals(r.cursor, "0")
        assert(r.values.isEmpty)
      }
    }
  }
}
