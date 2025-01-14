package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.arguments.{
  StreamRangeBound,
  StreamTrimStrategy
}
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

class StreamCommandsSuite extends ValkeyTestSuite {

  test("XADD should add an entry and return an ID") {
    valkeyClient.use { valkey =>
      for {
        id <- valkey.xadd(
          "stream-add-1",
          Map("field1" -> "value1", "field2" -> "value2")
        )
      } yield {
        val Ok(entryId) = id: @unchecked
        assert(
          entryId.contains("-"),
          s"Expected stream ID format, got: $entryId"
        )
      }
    }
  }

  test("XADD multiple entries should return increasing IDs") {
    valkeyClient.use { valkey =>
      for {
        id1 <- valkey.xadd("stream-add-2", Map("k" -> "v1"))
        id2 <- valkey.xadd("stream-add-2", Map("k" -> "v2"))
      } yield {
        val Ok(entryId1) = id1: @unchecked
        val Ok(entryId2) = id2: @unchecked
        assert(entryId1 < entryId2, s"Expected $entryId1 < $entryId2")
      }
    }
  }

  test("XLEN should return the number of entries") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-len-1", Map("a" -> "1"))
        _ <- valkey.xadd("stream-len-1", Map("b" -> "2"))
        _ <- valkey.xadd("stream-len-1", Map("c" -> "3"))
        len <- valkey.xlen("stream-len-1")
      } yield assertEquals(len, Ok(3L))
    }
  }

  test("XLEN on non-existent key should return 0") {
    valkeyClient.use { valkey =>
      for {
        len <- valkey.xlen("stream-len-nonexistent")
      } yield assertEquals(len, Ok(0L))
    }
  }

  test("XDEL should delete entries by ID") {
    valkeyClient.use { valkey =>
      for {
        id1 <- valkey.xadd("stream-del-1", Map("a" -> "1"))
        id2 <- valkey.xadd("stream-del-1", Map("b" -> "2"))
        _ <- valkey.xadd("stream-del-1", Map("c" -> "3"))
        deleted <- {
          val Ok(eid1) = id1: @unchecked
          val Ok(eid2) = id2: @unchecked
          valkey.xdel("stream-del-1", eid1, eid2)
        }
        len <- valkey.xlen("stream-del-1")
      } yield {
        assertEquals(deleted, Ok(2L))
        assertEquals(len, Ok(1L))
      }
    }
  }

  test("XDEL with non-existent ID should return 0") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-del-2", Map("a" -> "1"))
        deleted <- valkey.xdel("stream-del-2", "0-999999")
      } yield assertEquals(deleted, Ok(0L))
    }
  }

  test("XTRIM MaxLen should trim to specified length") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-trim-1", Map("a" -> "1"))
        _ <- valkey.xadd("stream-trim-1", Map("b" -> "2"))
        _ <- valkey.xadd("stream-trim-1", Map("c" -> "3"))
        _ <- valkey.xadd("stream-trim-1", Map("d" -> "4"))
        trimmed <- valkey.xtrim("stream-trim-1", StreamTrimStrategy.MaxLen(2))
        len <- valkey.xlen("stream-trim-1")
      } yield {
        assertEquals(trimmed, Ok(2L))
        assertEquals(len, Ok(2L))
      }
    }
  }

  test("XRANGE should return entries in forward order") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-range-1", Map("f" -> "first"))
        _ <- valkey.xadd("stream-range-1", Map("f" -> "second"))
        _ <- valkey.xadd("stream-range-1", Map("f" -> "third"))
        result <- valkey.xrange(
          "stream-range-1",
          StreamRangeBound.Min,
          StreamRangeBound.Max
        )
      } yield {
        val Ok(entries) = result: @unchecked
        assertEquals(entries.size, 3)
        val values = entries.toList.sortBy(_._1).flatMap(_._2.map(_._2))
        assertEquals(values, List("first", "second", "third"))
      }
    }
  }

  test("XRANGE with count should limit results") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-range-2", Map("f" -> "a"))
        _ <- valkey.xadd("stream-range-2", Map("f" -> "b"))
        _ <- valkey.xadd("stream-range-2", Map("f" -> "c"))
        result <- valkey.xrange(
          "stream-range-2",
          StreamRangeBound.Min,
          StreamRangeBound.Max,
          2
        )
      } yield {
        val Ok(entries) = result: @unchecked
        assertEquals(entries.size, 2)
      }
    }
  }

  test("XRANGE with ID bounds should filter entries") {
    valkeyClient.use { valkey =>
      for {
        id1 <- valkey.xadd("stream-range-3", Map("f" -> "a"))
        id2 <- valkey.xadd("stream-range-3", Map("f" -> "b"))
        id3 <- valkey.xadd("stream-range-3", Map("f" -> "c"))
        result <- {
          val Ok(eid1) = id1: @unchecked
          val Ok(eid3) = id3: @unchecked
          valkey.xrange(
            "stream-range-3",
            StreamRangeBound.ExclusiveId(eid1),
            StreamRangeBound.Id(eid3)
          )
        }
      } yield {
        val Ok(entries) = result: @unchecked
        assertEquals(entries.size, 2)
      }
    }
  }

  test("XREVRANGE should return entries in reverse order") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-revrange-1", Map("f" -> "first"))
        _ <- valkey.xadd("stream-revrange-1", Map("f" -> "second"))
        _ <- valkey.xadd("stream-revrange-1", Map("f" -> "third"))
        result <- valkey.xrevrange(
          "stream-revrange-1",
          StreamRangeBound.Max,
          StreamRangeBound.Min
        )
      } yield {
        val Ok(entries) = result: @unchecked
        assertEquals(entries.size, 3)
        val values = entries.toList.sortBy(_._1).reverse.flatMap(_._2.map(_._2))
        assertEquals(values, List("third", "second", "first"))
      }
    }
  }

  test("XREVRANGE with count should limit results from the end") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-revrange-2", Map("f" -> "a"))
        _ <- valkey.xadd("stream-revrange-2", Map("f" -> "b"))
        _ <- valkey.xadd("stream-revrange-2", Map("f" -> "c"))
        result <- valkey.xrevrange(
          "stream-revrange-2",
          StreamRangeBound.Max,
          StreamRangeBound.Min,
          2
        )
      } yield {
        val Ok(entries) = result: @unchecked
        assertEquals(entries.size, 2)
        val values = entries.toList.sortBy(_._1).reverse.flatMap(_._2.map(_._2))
        assertEquals(values, List("c", "b"))
      }
    }
  }

  test("XTRIM MinId should remove entries below the specified ID") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-trim-2", Map("a" -> "1"))
        id2 <- valkey.xadd("stream-trim-2", Map("b" -> "2"))
        _ <- valkey.xadd("stream-trim-2", Map("c" -> "3"))
        trimmed <- {
          val Ok(eid2) = id2: @unchecked
          valkey.xtrim("stream-trim-2", StreamTrimStrategy.MinId(eid2))
        }
        len <- valkey.xlen("stream-trim-2")
      } yield {
        assertEquals(trimmed, Ok(1L))
        assertEquals(len, Ok(2L))
      }
    }
  }

  // ==================== Consumer Group Commands ====================

  test("XGROUP CREATE and DESTROY should manage groups") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-grp-1", Map("f" -> "v"))
        create <- valkey.xgroupCreate("stream-grp-1", "grp1", "0")
        destroy <- valkey.xgroupDestroy("stream-grp-1", "grp1")
      } yield {
        assertEquals(create, Ok(()))
        assertEquals(destroy, Ok(true))
      }
    }
  }

  test("XGROUP CREATE with MKSTREAM should create the stream") {
    valkeyClient.use { valkey =>
      for {
        create <- valkey.xgroupCreate(
          "stream-grp-mkstream",
          "grp1",
          "0",
          mkStream = true
        )
        exists <- valkey.exists("stream-grp-mkstream")
        _ <- valkey.xgroupDestroy("stream-grp-mkstream", "grp1")
        _ <- valkey.del("stream-grp-mkstream")
      } yield {
        assertEquals(create, Ok(()))
        assertEquals(exists, Ok(true))
      }
    }
  }

  test("XGROUP CREATECONSUMER and DELCONSUMER should manage consumers") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-grp-cons", Map("f" -> "v"))
        _ <- valkey.xgroupCreate("stream-grp-cons", "grp1", "0")
        created <- valkey.xgroupCreateConsumer(
          "stream-grp-cons",
          "grp1",
          "consumer1"
        )
        deleted <- valkey.xgroupDelConsumer(
          "stream-grp-cons",
          "grp1",
          "consumer1"
        )
        _ <- valkey.xgroupDestroy("stream-grp-cons", "grp1")
      } yield {
        assertEquals(created, Ok(true))
        assertEquals(deleted, Ok(0L))
      }
    }
  }

  test("XGROUP SETID should update the last delivered ID") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-grp-setid", Map("f" -> "v"))
        _ <- valkey.xgroupCreate("stream-grp-setid", "grp1", "0")
        result <- valkey.xgroupSetId("stream-grp-setid", "grp1", "$")
        _ <- valkey.xgroupDestroy("stream-grp-setid", "grp1")
      } yield assertEquals(result, Ok(()))
    }
  }

  test("XACK should acknowledge messages") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-ack-1", Map("f" -> "v1"))
        id2 <- valkey.xadd("stream-ack-1", Map("f" -> "v2"))
        _ <- valkey.xgroupCreate("stream-ack-1", "grp1", "0")
        _ <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-ack-1" -> ">")
        )
        acked <- {
          val Ok(eid2) = id2: @unchecked
          valkey.xack("stream-ack-1", "grp1", eid2)
        }
        _ <- valkey.xgroupDestroy("stream-ack-1", "grp1")
      } yield assertEquals(acked, Ok(1L))
    }
  }

  test("XREAD should read from multiple streams") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-read-1", Map("f" -> "a"))
        _ <- valkey.xadd("stream-read-1", Map("f" -> "b"))
        result <- valkey.xread(Map("stream-read-1" -> "0"))
      } yield {
        val Ok(Some(streams)) = result: @unchecked
        assert(streams.contains("stream-read-1"))
        assertEquals(streams("stream-read-1").size, 2)
      }
    }
  }

  test("XREAD with count should limit results") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-read-2", Map("f" -> "a"))
        _ <- valkey.xadd("stream-read-2", Map("f" -> "b"))
        _ <- valkey.xadd("stream-read-2", Map("f" -> "c"))
        result <- valkey.xread(
          Map("stream-read-2" -> "0"),
          count = 1,
          block = 0
        )
      } yield {
        val Ok(Some(streams)) = result: @unchecked
        assertEquals(streams("stream-read-2").size, 1)
      }
    }
  }

  test("XREAD from non-existent stream should return None") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.xread(Map("stream-read-nonexistent" -> "0"))
      } yield {
        val Ok(value) = result: @unchecked
        assertEquals(value, None)
      }
    }
  }

  test("XREADGROUP should read messages for a consumer") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-rg-1", Map("f" -> "v1"))
        _ <- valkey.xadd("stream-rg-1", Map("f" -> "v2"))
        _ <- valkey.xgroupCreate("stream-rg-1", "grp1", "0")
        result <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-rg-1" -> ">")
        )
        _ <- valkey.xgroupDestroy("stream-rg-1", "grp1")
      } yield {
        val Ok(Some(streams)) = result: @unchecked
        assert(streams.contains("stream-rg-1"))
        assertEquals(streams("stream-rg-1").size, 2)
      }
    }
  }

  test("XREADGROUP with > should only read new messages") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-rg-2", Map("f" -> "v1"))
        _ <- valkey.xgroupCreate("stream-rg-2", "grp1", "0")
        read1 <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-rg-2" -> ">")
        )
        _ <- valkey.xadd("stream-rg-2", Map("f" -> "v2"))
        read2 <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-rg-2" -> ">")
        )
        _ <- valkey.xgroupDestroy("stream-rg-2", "grp1")
      } yield {
        val Ok(Some(s1)) = read1: @unchecked
        val Ok(Some(s2)) = read2: @unchecked
        assertEquals(s1("stream-rg-2").size, 1)
        assertEquals(s2("stream-rg-2").size, 1)
      }
    }
  }

  test("XREADGROUP with count should limit results") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-rg-3", Map("f" -> "v1"))
        _ <- valkey.xadd("stream-rg-3", Map("f" -> "v2"))
        _ <- valkey.xadd("stream-rg-3", Map("f" -> "v3"))
        _ <- valkey.xgroupCreate("stream-rg-3", "grp1", "0")
        result <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-rg-3" -> ">"),
          count = 2,
          block = 0
        )
        _ <- valkey.xgroupDestroy("stream-rg-3", "grp1")
      } yield {
        val Ok(Some(streams)) = result: @unchecked
        assertEquals(streams("stream-rg-3").size, 2)
      }
    }
  }

  test("XREADGROUP with noAck should not add to pending") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xadd("stream-rg-4", Map("f" -> "v1"))
        _ <- valkey.xgroupCreate("stream-rg-4", "grp1", "0")
        _ <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-rg-4" -> ">"),
          count = 10,
          block = 0,
          noAck = true
        )
        summary <- valkey.xpendingSummary("stream-rg-4", "grp1")
        _ <- valkey.xgroupDestroy("stream-rg-4", "grp1")
      } yield {
        val Ok(s) = summary: @unchecked
        assertEquals(s.pendingCount, 0L)
      }
    }
  }

  // ==================== XCLAIM ====================

  test("XCLAIM should claim pending messages") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xgroupCreate("stream-claim-1", "grp1", "0", true)
        id1 <- valkey.xadd("stream-claim-1", Map("k" -> "v1"))
        id2 <- valkey.xadd("stream-claim-1", Map("k" -> "v2"))
        _ <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-claim-1" -> ">")
        )
        claimed <- valkey.xclaim(
          "stream-claim-1",
          "grp1",
          "consumer2",
          0L,
          id1.toOption.get,
          id2.toOption.get
        )
        _ <- valkey.xgroupDestroy("stream-claim-1", "grp1")
      } yield {
        val Ok(entries) = claimed: @unchecked
        assertEquals(entries.size, 2)
      }
    }
  }

  test("XCLAIM on non-existent IDs should return empty") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xgroupCreate("stream-claim-2", "grp1", "0", true)
        _ <- valkey.xadd("stream-claim-2", Map("k" -> "v1"))
        claimed <- valkey.xclaim(
          "stream-claim-2",
          "grp1",
          "consumer1",
          0L,
          "99999-0"
        )
        _ <- valkey.xgroupDestroy("stream-claim-2", "grp1")
      } yield {
        val Ok(entries) = claimed: @unchecked
        assert(entries.isEmpty)
      }
    }
  }

  // ==================== XPENDING ====================

  test("XPENDING summary should return pending count and consumer info") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xgroupCreate("stream-pend-1", "grp1", "0", true)
        id1 <- valkey.xadd("stream-pend-1", Map("k" -> "v1"))
        id2 <- valkey.xadd("stream-pend-1", Map("k" -> "v2"))
        _ <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-pend-1" -> ">")
        )
        summary <- valkey.xpendingSummary("stream-pend-1", "grp1")
        _ <- valkey.xack(
          "stream-pend-1",
          "grp1",
          id1.toOption.get,
          id2.toOption.get
        )
        _ <- valkey.xgroupDestroy("stream-pend-1", "grp1")
      } yield {
        val Ok(s) = summary: @unchecked
        assertEquals(s.pendingCount, 2L)
        assert(s.smallestId.isDefined)
        assert(s.greatestId.isDefined)
        assertEquals(s.consumers.size, 1)
        assertEquals(s.consumers.head.consumer, "consumer1")
        assertEquals(s.consumers.head.pendingCount, 2L)
      }
    }
  }

  test("XPENDING summary on empty group should return zero count") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xgroupCreate("stream-pend-2", "grp1", "0", true)
        _ <- valkey.xadd("stream-pend-2", Map("k" -> "v1"))
        summary <- valkey.xpendingSummary("stream-pend-2", "grp1")
        _ <- valkey.xgroupDestroy("stream-pend-2", "grp1")
      } yield {
        val Ok(s) = summary: @unchecked
        assertEquals(s.pendingCount, 0L)
        assert(s.smallestId.isEmpty || s.smallestId.contains(""))
        assert(s.consumers.isEmpty)
      }
    }
  }

  test("XPENDING range should return detailed pending entries") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xgroupCreate("stream-pend-3", "grp1", "0", true)
        id1 <- valkey.xadd("stream-pend-3", Map("k" -> "v1"))
        id2 <- valkey.xadd("stream-pend-3", Map("k" -> "v2"))
        _ <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-pend-3" -> ">")
        )
        pending <- valkey.xpendingRange(
          "stream-pend-3",
          "grp1",
          StreamRangeBound.Min,
          StreamRangeBound.Max,
          10
        )
        _ <- valkey.xack(
          "stream-pend-3",
          "grp1",
          id1.toOption.get,
          id2.toOption.get
        )
        _ <- valkey.xgroupDestroy("stream-pend-3", "grp1")
      } yield {
        val Ok(entries) = pending: @unchecked
        assertEquals(entries.size, 2)
        entries.foreach { entry =>
          assert(entry.messageId.nonEmpty)
          assertEquals(entry.consumer, "consumer1")
          assert(entry.idleTimeMillis >= 0)
          assertEquals(entry.deliveryCount, 1L)
        }
      }
    }
  }

  test("XPENDING range on empty group should return empty list") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xgroupCreate("stream-pend-4", "grp1", "0", true)
        pending <- valkey.xpendingRange(
          "stream-pend-4",
          "grp1",
          StreamRangeBound.Min,
          StreamRangeBound.Max,
          10
        )
        _ <- valkey.xgroupDestroy("stream-pend-4", "grp1")
      } yield {
        val Ok(entries) = pending: @unchecked
        assert(entries.isEmpty)
      }
    }
  }

  // ==================== XAUTOCLAIM ====================

  test("XAUTOCLAIM should claim idle pending messages") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xgroupCreate("stream-ac-1", "grp1", "0", true)
        id1 <- valkey.xadd("stream-ac-1", Map("k" -> "v1"))
        id2 <- valkey.xadd("stream-ac-1", Map("k" -> "v2"))
        _ <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-ac-1" -> ">")
        )
        result <- valkey.xautoclaim(
          "stream-ac-1",
          "grp1",
          "consumer2",
          0L,
          "0-0"
        )
        _ <- valkey.xack(
          "stream-ac-1",
          "grp1",
          id1.toOption.get,
          id2.toOption.get
        )
        _ <- valkey.xgroupDestroy("stream-ac-1", "grp1")
      } yield {
        val Ok(r) = result: @unchecked
        assert(r.nextCursor.nonEmpty)
        assertEquals(r.claimedEntries.size, 2)
      }
    }
  }

  test("XAUTOCLAIM with count should limit claimed messages") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xgroupCreate("stream-ac-2", "grp1", "0", true)
        id1 <- valkey.xadd("stream-ac-2", Map("k" -> "v1"))
        id2 <- valkey.xadd("stream-ac-2", Map("k" -> "v2"))
        _ <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-ac-2" -> ">")
        )
        result <- valkey.xautoclaim(
          "stream-ac-2",
          "grp1",
          "consumer2",
          0L,
          "0-0",
          1L
        )
        _ <- valkey.xack(
          "stream-ac-2",
          "grp1",
          id1.toOption.get,
          id2.toOption.get
        )
        _ <- valkey.xgroupDestroy("stream-ac-2", "grp1")
      } yield {
        val Ok(r) = result: @unchecked
        assertEquals(r.claimedEntries.size, 1)
      }
    }
  }

  test("XAUTOCLAIM JUSTID should return only message IDs") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.xgroupCreate("stream-ac-3", "grp1", "0", true)
        id1 <- valkey.xadd("stream-ac-3", Map("k" -> "v1"))
        id2 <- valkey.xadd("stream-ac-3", Map("k" -> "v2"))
        _ <- valkey.xreadgroup(
          "grp1",
          "consumer1",
          Map("stream-ac-3" -> ">")
        )
        result <- valkey.xautoclaimJustId(
          "stream-ac-3",
          "grp1",
          "consumer2",
          0L,
          "0-0"
        )
        _ <- valkey.xack(
          "stream-ac-3",
          "grp1",
          id1.toOption.get,
          id2.toOption.get
        )
        _ <- valkey.xgroupDestroy("stream-ac-3", "grp1")
      } yield {
        val Ok(r) = result: @unchecked
        assert(r.nextCursor.nonEmpty)
        assertEquals(r.claimedIds.size, 2)
        assert(r.claimedIds.forall(_.contains("-")))
      }
    }
  }
}
