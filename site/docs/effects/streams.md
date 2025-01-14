---
layout: docs
title:  "Streams"
number: 18
---

# Streams API

Purely functional interface for [Valkey Streams](https://valkey.io/commands/#stream).

Valkey Streams are an append-only log data structure. Unlike redis4cats which wraps streams in Fs2, Valkey4Cats exposes the raw stream commands directly as `F[ValkeyResponse[A]]`, giving you full control over how you consume stream data.

### Basic Stream Operations

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}
import dev.profunktor.valkey4cats.arguments.{StreamRangeBound, StreamTrimStrategy}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // XADD - append entries to a stream
    id1 <- valkey.xadd("events", Map("type" -> "click", "page" -> "/home"))
    _ <- IO.println(s"Added entry: ${id1.toOption}")

    id2 <- valkey.xadd("events", Map("type" -> "purchase", "item" -> "widget"))
    _ <- IO.println(s"Added entry: ${id2.toOption}")

    // XLEN - get stream length
    len <- valkey.xlen("events")
    _ <- IO.println(s"Stream length: ${len.toOption}") // Some(2)

    // XRANGE - read entries in a range
    entries <- valkey.xrange("events", StreamRangeBound.Min, StreamRangeBound.Max)
    _ <- entries match
      case Ok(map) =>
        map.foreach { case (id, fields) =>
          IO.println(s"  $id: $fields")
        }
        IO.unit
      case Err(e) => IO.println(s"Error: ${e.message}")

    // XRANGE with count limit
    limited <- valkey.xrange("events", StreamRangeBound.Min, StreamRangeBound.Max, 1)
    _ <- IO.println(s"First entry: ${limited.toOption}")

    // XREVRANGE - read entries in reverse
    reversed <- valkey.xrevrange("events", StreamRangeBound.Max, StreamRangeBound.Min)
    _ <- IO.println(s"Reversed: ${reversed.toOption}")

    // XDEL - delete entries
    deleted <- valkey.xdel("events", id1.toOption.getOrElse("0-0"))
    _ <- IO.println(s"Deleted: ${deleted.toOption}")

    // XTRIM - trim stream to max length
    trimmed <- valkey.xtrim("events", StreamTrimStrategy.MaxLen(1000))
    _ <- IO.println(s"Trimmed: ${trimmed.toOption}")
  yield ()
}
```

### Consumer Groups

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}
import dev.profunktor.valkey4cats.arguments.StreamRangeBound

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // Create the stream with an entry
    _ <- valkey.xadd("mystream", Map("data" -> "value1"))

    // XGROUP CREATE - create a consumer group
    _ <- valkey.xgroupCreate("mystream", "mygroup", "0")
    // Or create the stream if it doesn't exist:
    // _ <- valkey.xgroupCreate("mystream", "mygroup", "0", mkStream = true)

    // Add more entries
    _ <- valkey.xadd("mystream", Map("data" -> "value2"))
    _ <- valkey.xadd("mystream", Map("data" -> "value3"))

    // XREADGROUP - read as a consumer in the group
    // ">" means only new messages not yet delivered to this consumer
    messages <- valkey.xreadgroup(
      "mygroup", "consumer1",
      Map("mystream" -> ">")
    )
    _ <- messages match
      case Ok(Some(streamMap)) =>
        IO.println(s"Received: $streamMap")
      case Ok(None) =>
        IO.println("No new messages")
      case Err(e) =>
        IO.println(s"Error: ${e.message}")

    // XREADGROUP with count and block
    blocked <- valkey.xreadgroup(
      "mygroup", "consumer1",
      Map("mystream" -> ">"),
      count = 10,
      block = 1000  // block for 1 second max
    )
    _ <- IO.println(s"Blocked read: ${blocked.toOption}")

    // XACK - acknowledge processed messages
    _ <- valkey.xack("mystream", "mygroup", "1234567890-0")

    // XPENDING - check pending messages
    summary <- valkey.xpendingSummary("mystream", "mygroup")
    _ <- IO.println(s"Pending summary: ${summary.toOption}")

    // XPENDING with range
    pending <- valkey.xpendingRange(
      "mystream", "mygroup",
      StreamRangeBound.Min, StreamRangeBound.Max,
      10
    )
    _ <- IO.println(s"Pending entries: ${pending.toOption}")

    // XCLAIM - claim pending messages from another consumer
    claimed <- valkey.xclaim(
      "mystream", "mygroup", "consumer2",
      minIdleTimeMillis = 60000,  // idle for at least 1 minute
      "1234567890-0"
    )
    _ <- IO.println(s"Claimed: ${claimed.toOption}")

    // XAUTOCLAIM - automatically claim idle messages
    autoClaimed <- valkey.xautoclaim(
      "mystream", "mygroup", "consumer2",
      minIdleTimeMillis = 30000,
      start = "0-0"
    )
    _ <- IO.println(s"Auto-claimed: ${autoClaimed.toOption}")

    // Group management
    _ <- valkey.xgroupCreateConsumer("mystream", "mygroup", "consumer3")
    _ <- valkey.xgroupDelConsumer("mystream", "mygroup", "consumer3")
    _ <- valkey.xgroupSetId("mystream", "mygroup", "$")

    // XGROUP DESTROY
    // _ <- valkey.xgroupDestroy("mystream", "mygroup")
  yield ()
}
```

### XREAD (without consumer groups)

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // XREAD - read from multiple streams
    // Read entries after ID "0" (i.e., all entries)
    result <- valkey.xread(Map("stream1" -> "0", "stream2" -> "0"))
    _ <- IO.println(s"Read: ${result.toOption}")

    // XREAD with blocking
    blocked <- valkey.xread(
      Map("stream1" -> "$"),  // "$" means only new entries
      count = 5,
      block = 2000  // block for 2 seconds
    )
    _ <- IO.println(s"Blocked read: ${blocked.toOption}")
  yield ()
}
```

### Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| XADD | `xadd(key, fieldValues)` | `F[ValkeyResponse[String]]` |
| XLEN | `xlen(key)` | `F[ValkeyResponse[Long]]` |
| XDEL | `xdel(key, ids*)` | `F[ValkeyResponse[Long]]` |
| XTRIM | `xtrim(key, strategy)` | `F[ValkeyResponse[Long]]` |
| XRANGE | `xrange(key, start, end)` | `F[ValkeyResponse[Map[String, List[(K, V)]]]]` |
| XRANGE (count) | `xrange(key, start, end, count)` | `F[ValkeyResponse[Map[String, List[(K, V)]]]]` |
| XREVRANGE | `xrevrange(key, end, start)` | `F[ValkeyResponse[Map[String, List[(K, V)]]]]` |
| XREAD | `xread(keysAndIds)` | `F[ValkeyResponse[Option[Map[K, ...]]]]` |
| XREAD (block) | `xread(keysAndIds, count, block)` | `F[ValkeyResponse[Option[Map[K, ...]]]]` |
| XGROUP CREATE | `xgroupCreate(key, group, id)` | `F[ValkeyResponse[Unit]]` |
| XGROUP DESTROY | `xgroupDestroy(key, group)` | `F[ValkeyResponse[Boolean]]` |
| XGROUP CREATECONSUMER | `xgroupCreateConsumer(key, group, consumer)` | `F[ValkeyResponse[Boolean]]` |
| XGROUP DELCONSUMER | `xgroupDelConsumer(key, group, consumer)` | `F[ValkeyResponse[Long]]` |
| XGROUP SETID | `xgroupSetId(key, group, id)` | `F[ValkeyResponse[Unit]]` |
| XREADGROUP | `xreadgroup(group, consumer, keysAndIds)` | `F[ValkeyResponse[Option[Map[K, ...]]]]` |
| XACK | `xack(key, group, ids*)` | `F[ValkeyResponse[Long]]` |
| XCLAIM | `xclaim(key, group, consumer, minIdle, ids*)` | `F[ValkeyResponse[Map[String, List[(K, V)]]]]` |
| XPENDING (summary) | `xpendingSummary(key, group)` | `F[ValkeyResponse[PendingSummary[K]]]` |
| XPENDING (range) | `xpendingRange(key, group, start, end, count)` | `F[ValkeyResponse[List[PendingEntry[K]]]]` |
| XAUTOCLAIM | `xautoclaim(key, group, consumer, minIdle, start)` | `F[ValkeyResponse[AutoClaimResult[K, V]]]` |
| XAUTOCLAIM JUSTID | `xautoclaimJustId(key, group, consumer, minIdle, start)` | `F[ValkeyResponse[AutoClaimIdResult]]` |
