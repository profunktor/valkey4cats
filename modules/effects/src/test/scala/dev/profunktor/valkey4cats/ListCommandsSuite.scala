package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.arguments.{InsertPosition, ListDirection}
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok
import dev.profunktor.valkey4cats.results.InsertResult

class ListCommandsSuite extends ValkeyTestSuite {

  test("LPUSH should add elements to the head of list") {
    valkeyClient.use { valkey =>
      for {
        length <- valkey.lpush("list1", "third", "second", "first")
        result <- valkey.lrange("list1", 0, -1)
        _ <- valkey.del("list1")
      } yield {
        assertEquals(length, Ok(3L))
        assertEquals(result, Ok(List("first", "second", "third")))
      }
    }
  }

  test("RPUSH should add elements to the tail of list") {
    valkeyClient.use { valkey =>
      for {
        length <- valkey.rpush("list2", "first", "second", "third")
        result <- valkey.lrange("list2", 0, -1)
        _ <- valkey.del("list2")
      } yield {
        assertEquals(length, Ok(3L))
        assertEquals(result, Ok(List("first", "second", "third")))
      }
    }
  }

  test("LPOP should remove and return first element") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list3", "first", "second", "third")
        popped <- valkey.lpop("list3")
        remaining <- valkey.lrange("list3", 0, -1)
        _ <- valkey.del("list3")
      } yield {
        assertEquals(popped, Ok(Some("first")))
        assertEquals(remaining, Ok(List("second", "third")))
      }
    }
  }

  test("LPOP should return None for non-existent list") {
    valkeyClient.use { valkey =>
      for {
        popped <- valkey.lpop("non-existent")
      } yield assertEquals(popped, Ok(None))
    }
  }

  test("RPOP should remove and return last element") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list4", "first", "second", "third")
        popped <- valkey.rpop("list4")
        remaining <- valkey.lrange("list4", 0, -1)
        _ <- valkey.del("list4")
      } yield {
        assertEquals(popped, Ok(Some("third")))
        assertEquals(remaining, Ok(List("first", "second")))
      }
    }
  }

  test("LPOPCOUNT should remove and return multiple elements from head") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list5", "a", "b", "c", "d", "e")
        popped <- valkey.lpopCount("list5", 3)
        remaining <- valkey.lrange("list5", 0, -1)
        _ <- valkey.del("list5")
      } yield {
        assertEquals(popped, Ok(List("a", "b", "c")))
        assertEquals(remaining, Ok(List("d", "e")))
      }
    }
  }

  test("RPOPCOUNT should remove and return multiple elements from tail") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list6", "a", "b", "c", "d", "e")
        popped <- valkey.rpopCount("list6", 3)
        remaining <- valkey.lrange("list6", 0, -1)
        _ <- valkey.del("list6")
      } yield {
        assertEquals(popped, Ok(List("e", "d", "c")))
        assertEquals(remaining, Ok(List("a", "b")))
      }
    }
  }

  test("LRANGE should return all elements with 0 -1") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list7", "a", "b", "c", "d", "e")
        result <- valkey.lrange("list7", 0, -1)
        _ <- valkey.del("list7")
      } yield {
        assertEquals(result, Ok(List("a", "b", "c", "d", "e")))
      }
    }
  }

  test("LRANGE should return specific range") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list8", "a", "b", "c", "d", "e")
        result <- valkey.lrange("list8", 1, 3)
        _ <- valkey.del("list8")
      } yield {
        assertEquals(result, Ok(List("b", "c", "d")))
      }
    }
  }

  test("LRANGE should return empty list for non-existent key") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.lrange("non-existent", 0, -1)
      } yield assertEquals(result, Ok(List.empty[String]))
    }
  }

  test("LINDEX should return element at index") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list9", "a", "b", "c", "d", "e")
        elem0 <- valkey.lindex("list9", 0)
        elem2 <- valkey.lindex("list9", 2)
        elemLast <- valkey.lindex("list9", -1)
        _ <- valkey.del("list9")
      } yield {
        assertEquals(elem0, Ok(Some("a")))
        assertEquals(elem2, Ok(Some("c")))
        assertEquals(elemLast, Ok(Some("e")))
      }
    }
  }

  test("LINDEX should return None for out of range index") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list10", "a", "b", "c")
        result <- valkey.lindex("list10", 100)
        _ <- valkey.del("list10")
      } yield assertEquals(result, Ok(None))
    }
  }

  test("LLEN should return length of list") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list11", "a", "b", "c", "d", "e")
        length <- valkey.llen("list11")
        _ <- valkey.del("list11")
      } yield assertEquals(length, Ok(5L))
    }
  }

  test("LLEN should return 0 for non-existent list") {
    valkeyClient.use { valkey =>
      for {
        length <- valkey.llen("non-existent")
      } yield assertEquals(length, Ok(0L))
    }
  }

  test("LTRIM should trim list to specified range") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list12", "a", "b", "c", "d", "e")
        _ <- valkey.ltrim("list12", 1, 3)
        result <- valkey.lrange("list12", 0, -1)
        _ <- valkey.del("list12")
      } yield {
        assertEquals(result, Ok(List("b", "c", "d")))
      }
    }
  }

  test("LSET should set element at index") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list13", "a", "b", "c", "d", "e")
        _ <- valkey.lset("list13", 2, "CHANGED")
        result <- valkey.lrange("list13", 0, -1)
        _ <- valkey.del("list13")
      } yield {
        assertEquals(result, Ok(List("a", "b", "CHANGED", "d", "e")))
      }
    }
  }

  test("LREM should remove occurrences of element") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list14", "a", "b", "a", "c", "a", "d")
        count <- valkey.lrem("list14", 2, "a") // Remove first 2 occurrences
        result <- valkey.lrange("list14", 0, -1)
        _ <- valkey.del("list14")
      } yield {
        assertEquals(count, Ok(2L))
        assertEquals(result, Ok(List("b", "c", "a", "d")))
      }
    }
  }

  test("LREM with count=0 should remove all occurrences") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list15", "a", "b", "a", "c", "a", "d")
        count <- valkey.lrem("list15", 0, "a")
        result <- valkey.lrange("list15", 0, -1)
        _ <- valkey.del("list15")
      } yield {
        assertEquals(count, Ok(3L))
        assertEquals(result, Ok(List("b", "c", "d")))
      }
    }
  }

  test("LINSERT should insert element before pivot") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list16", "a", "b", "d", "e")
        insertResult <- valkey.linsert(
          "list16",
          InsertPosition.Before,
          "d",
          "c"
        )
        result <- valkey.lrange("list16", 0, -1)
        _ <- valkey.del("list16")
      } yield {
        assertEquals(insertResult, Ok(InsertResult.Inserted(5L)))
        assertEquals(result, Ok(List("a", "b", "c", "d", "e")))
      }
    }
  }

  test("LINSERT should insert element after pivot") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list17", "a", "b", "c", "e")
        insertResult <- valkey.linsert("list17", InsertPosition.After, "c", "d")
        result <- valkey.lrange("list17", 0, -1)
        _ <- valkey.del("list17")
      } yield {
        assertEquals(insertResult, Ok(InsertResult.Inserted(5L)))
        assertEquals(result, Ok(List("a", "b", "c", "d", "e")))
      }
    }
  }

  test("LINSERT should return PivotNotFound when pivot not found") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list18", "a", "b", "c")
        insertResult <- valkey.linsert(
          "list18",
          InsertPosition.Before,
          "x",
          "y"
        )
        _ <- valkey.del("list18")
      } yield assertEquals(insertResult, Ok(InsertResult.PivotNotFound))
    }
  }

  test("LPOS should return index of first occurrence") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list19", "a", "b", "c", "b", "d")
        index <- valkey.lpos("list19", "b")
        _ <- valkey.del("list19")
      } yield assertEquals(index, Ok(Some(1L)))
    }
  }

  test("LPOS should return None when element not found") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("list20", "a", "b", "c")
        index <- valkey.lpos("list20", "x")
        _ <- valkey.del("list20")
      } yield assertEquals(index, Ok(None))
    }
  }

  test("complex workflow: queue operations") {
    valkeyClient.use { valkey =>
      for {
        // Create a queue
        _ <- valkey.rpush("queue", "task1", "task2", "task3")

        // Check queue length
        length1 <- valkey.llen("queue")

        // Process first task (LPOP)
        task1 <- valkey.lpop("queue")

        // Add new tasks
        _ <- valkey.rpush("queue", "task4", "task5")

        // Check current queue
        currentQueue <- valkey.lrange("queue", 0, -1)

        // Process multiple tasks
        tasks <- valkey.lpopCount("queue", 2)

        // Final length
        finalLength <- valkey.llen("queue")

        // Cleanup
        _ <- valkey.del("queue")
      } yield {
        assertEquals(length1, Ok(3L))
        assertEquals(task1, Ok(Some("task1")))
        assertEquals(currentQueue, Ok(List("task2", "task3", "task4", "task5")))
        assertEquals(tasks, Ok(List("task2", "task3")))
        assertEquals(finalLength, Ok(2L))
      }
    }
  }

  test("complex workflow: stack operations (LIFO)") {
    valkeyClient.use { valkey =>
      for {
        // Push items (LPUSH acts as stack push)
        _ <- valkey.lpush("stack", "item1")
        _ <- valkey.lpush("stack", "item2")
        _ <- valkey.lpush("stack", "item3")

        // Pop items (LPOP acts as stack pop - LIFO order)
        item1 <- valkey.lpop("stack")
        item2 <- valkey.lpop("stack")
        item3 <- valkey.lpop("stack")

        // Cleanup
        _ <- valkey.del("stack")
      } yield {
        assertEquals(item1, Ok(Some("item3"))) // Last in
        assertEquals(item2, Ok(Some("item2")))
        assertEquals(item3, Ok(Some("item1"))) // First in
      }
    }
  }

  // ==================== LPUSHX / RPUSHX ====================

  test("LPUSHX should push only if key exists") {
    valkeyClient.use { valkey =>
      for {
        noKey <- valkey.lpushx("lpushx-nokey", "a")
        _ <- valkey.lpush("lpushx-exists", "a")
        pushed <- valkey.lpushx("lpushx-exists", "b")
        len <- valkey.llen("lpushx-exists")
        _ <- valkey.del("lpushx-exists")
      } yield {
        assertEquals(noKey, Ok(0L))
        assertEquals(pushed, Ok(2L))
        assertEquals(len, Ok(2L))
      }
    }
  }

  test("RPUSHX should push only if key exists") {
    valkeyClient.use { valkey =>
      for {
        noKey <- valkey.rpushx("rpushx-nokey", "a")
        _ <- valkey.rpush("rpushx-exists", "a")
        pushed <- valkey.rpushx("rpushx-exists", "b")
        items <- valkey.lrange("rpushx-exists", 0, -1)
        _ <- valkey.del("rpushx-exists")
      } yield {
        assertEquals(noKey, Ok(0L))
        assertEquals(pushed, Ok(2L))
        assertEquals(items, Ok(List("a", "b")))
      }
    }
  }

  // ==================== LMOVE ====================

  test("LMOVE should move element between lists") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("lmove-src", "a", "b", "c")
        moved <- valkey.lmove(
          "lmove-src",
          "lmove-dest",
          ListDirection.Left,
          ListDirection.Right
        )
        srcItems <- valkey.lrange("lmove-src", 0, -1)
        destItems <- valkey.lrange("lmove-dest", 0, -1)
        _ <- valkey.del("lmove-src", "lmove-dest")
      } yield {
        assertEquals(moved, Ok(Some("a")))
        assertEquals(srcItems, Ok(List("b", "c")))
        assertEquals(destItems, Ok(List("a")))
      }
    }
  }

  test("LMOVE from right to left") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("lmove-src2", "a", "b", "c")
        moved <- valkey.lmove(
          "lmove-src2",
          "lmove-dest2",
          ListDirection.Right,
          ListDirection.Left
        )
        _ <- valkey.del("lmove-src2", "lmove-dest2")
      } yield assertEquals(moved, Ok(Some("c")))
    }
  }

  test("LMOVE from empty list should return None") {
    valkeyClient.use { valkey =>
      for {
        moved <- valkey.lmove(
          "lmove-empty",
          "lmove-dest3",
          ListDirection.Left,
          ListDirection.Right
        )
      } yield assertEquals(moved, Ok(None))
    }
  }

  // ==================== BLPOP / BRPOP ====================

  test("BLPOP should pop from first non-empty list") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("blpop-list", "a", "b")
        result <- valkey.blpop(List("blpop-list"), 1.0)
        _ <- valkey.del("blpop-list")
      } yield {
        val Ok(Some((key, value))) = result: @unchecked
        assertEquals(key, "blpop-list")
        assertEquals(value, "a")
      }
    }
  }

  test("BLPOP should timeout on empty list") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.blpop(List("blpop-empty"), 0.1)
      } yield assertEquals(result, Ok(None))
    }
  }

  test("BRPOP should pop from first non-empty list") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("brpop-list", "a", "b")
        result <- valkey.brpop(List("brpop-list"), 1.0)
        _ <- valkey.del("brpop-list")
      } yield {
        val Ok(Some((key, value))) = result: @unchecked
        assertEquals(key, "brpop-list")
        assertEquals(value, "b")
      }
    }
  }

  test("BRPOP should timeout on empty list") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.brpop(List("brpop-empty"), 0.1)
      } yield assertEquals(result, Ok(None))
    }
  }

  test("BLPOP should check multiple keys in order") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("blpop-second", "value")
        result <- valkey.blpop(List("blpop-first-empty", "blpop-second"), 1.0)
        _ <- valkey.del("blpop-second")
      } yield {
        val Ok(Some((key, value))) = result: @unchecked
        assertEquals(key, "blpop-second")
        assertEquals(value, "value")
      }
    }
  }

  // ==================== LPOSCOUNT ====================

  test("LPOSCOUNT should return indices of all matches") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("lpc1", "a", "b", "a", "c", "a", "d")
        indices <- valkey.lposCount("lpc1", "a", 0)
        _ <- valkey.del("lpc1")
      } yield assertEquals(indices, Ok(List(0L, 2L, 4L)))
    }
  }

  test("LPOSCOUNT should limit number of matches") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("lpc2", "a", "b", "a", "c", "a", "d")
        indices <- valkey.lposCount("lpc2", "a", 2)
        _ <- valkey.del("lpc2")
      } yield assertEquals(indices, Ok(List(0L, 2L)))
    }
  }

  test("LPOSCOUNT should return empty list when not found") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("lpc3", "a", "b", "c")
        indices <- valkey.lposCount("lpc3", "x", 0)
        _ <- valkey.del("lpc3")
      } yield assertEquals(indices, Ok(List.empty[Long]))
    }
  }

  // ==================== BLMOVE ====================

  test("BLMOVE should move element between lists") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("blm-src", "a", "b", "c")
        result <- valkey.blmove(
          "blm-src",
          "blm-dest",
          ListDirection.Left,
          ListDirection.Right,
          1.0
        )
        srcItems <- valkey.lrange("blm-src", 0, -1)
        destItems <- valkey.lrange("blm-dest", 0, -1)
        _ <- valkey.del("blm-src", "blm-dest")
      } yield {
        assertEquals(result, Ok(Some("a")))
        assertEquals(srcItems, Ok(List("b", "c")))
        assertEquals(destItems, Ok(List("a")))
      }
    }
  }

  test("BLMOVE should timeout on empty source") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.blmove(
          "blm-empty",
          "blm-dest2",
          ListDirection.Left,
          ListDirection.Right,
          0.1
        )
      } yield assertEquals(result, Ok(None))
    }
  }

  test("BLMOVE from right to left") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("blm-src2", "a", "b", "c")
        result <- valkey.blmove(
          "blm-src2",
          "blm-dest3",
          ListDirection.Right,
          ListDirection.Left,
          1.0
        )
        _ <- valkey.del("blm-src2", "blm-dest3")
      } yield assertEquals(result, Ok(Some("c")))
    }
  }

  // ==================== LMPOP / BLMPOP ====================

  test("LMPOP should pop from the first non-empty list (left)") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("lmpop-1", "a", "b", "c")
        result <- valkey.lmpop(List("lmpop-1"), ListDirection.Left)
        _ <- valkey.del("lmpop-1")
      } yield {
        val Ok(Some((key, elements))) = result: @unchecked
        assertEquals(key, "lmpop-1")
        assertEquals(elements, List("a"))
      }
    }
  }

  test("LMPOP with count should pop multiple elements") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("lmpop-2", "a", "b", "c", "d")
        result <- valkey.lmpop(List("lmpop-2"), ListDirection.Left, 2)
        _ <- valkey.del("lmpop-2")
      } yield {
        val Ok(Some((key, elements))) = result: @unchecked
        assertEquals(key, "lmpop-2")
        assertEquals(elements, List("a", "b"))
      }
    }
  }

  test("LMPOP from right should pop from the tail") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("lmpop-3", "a", "b", "c")
        result <- valkey.lmpop(List("lmpop-3"), ListDirection.Right, 2)
        _ <- valkey.del("lmpop-3")
      } yield {
        val Ok(Some((key, elements))) = result: @unchecked
        assertEquals(key, "lmpop-3")
        assertEquals(elements, List("c", "b"))
      }
    }
  }

  test("LMPOP from empty lists should return None") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.lmpop(
          List("lmpop-empty-1", "lmpop-empty-2"),
          ListDirection.Left
        )
      } yield {
        val Ok(value) = result: @unchecked
        assertEquals(value, None)
      }
    }
  }

  test("BLMPOP should pop from available list") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("blmpop-1", "x", "y", "z")
        result <- valkey.blmpop(
          List("blmpop-1"),
          ListDirection.Left,
          1.0
        )
        _ <- valkey.del("blmpop-1")
      } yield {
        val Ok(Some((key, elements))) = result: @unchecked
        assertEquals(key, "blmpop-1")
        assertEquals(elements, List("x"))
      }
    }
  }

  test("BLMPOP with count should pop multiple elements") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.rpush("blmpop-2", "a", "b", "c")
        result <- valkey.blmpop(
          List("blmpop-2"),
          ListDirection.Left,
          2,
          1.0
        )
        _ <- valkey.del("blmpop-2")
      } yield {
        val Ok(Some((key, elements))) = result: @unchecked
        assertEquals(key, "blmpop-2")
        assertEquals(elements, List("a", "b"))
      }
    }
  }
}
