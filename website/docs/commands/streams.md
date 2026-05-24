---
sidebar_position: 8
title: Streams
---

# Streams API

Purely functional interface for [Valkey Streams](https://valkey.io/commands/#stream).

Valkey Streams are an append-only log data structure. Valkey4Cats exposes the raw stream commands directly as `F[ValkeyResponse[A]]`, giving you full control over how you consume stream data.

## Basic operations

```scala
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

    // XLEN - get stream length
    len <- valkey.xlen("events")
    _ <- IO.println(s"Stream length: ${len.toOption}")

    // XRANGE - read entries in a range
    entries <- valkey.xrange("events", StreamRangeBound.Min, StreamRangeBound.Max)
    _ <- IO.println(s"Entries: ${entries.toOption}")

    // XTRIM - trim stream to max length
    trimmed <- valkey.xtrim("events", StreamTrimStrategy.MaxLen(1000))
    _ <- IO.println(s"Trimmed: ${trimmed.toOption}")
  yield ()
}
```

## Consumer groups

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    _ <- valkey.xadd("mystream", Map("data" -> "value1"))

    // XGROUP CREATE
    _ <- valkey.xgroupCreate("mystream", "mygroup", "0")

    _ <- valkey.xadd("mystream", Map("data" -> "value2"))

    // XREADGROUP - read as a consumer in the group
    messages <- valkey.xreadgroup(
      "mygroup", "consumer1",
      Map("mystream" -> ">")
    )
    _ <- messages match
      case Ok(Some(streamMap)) => IO.println(s"Received: $streamMap")
      case Ok(None)            => IO.println("No new messages")
      case Err(e)              => IO.println(s"Error: ${e.message}")

    // XACK - acknowledge processed messages
    _ <- valkey.xack("mystream", "mygroup", "1234567890-0")

    // XPENDING - check pending messages
    summary <- valkey.xpendingSummary("mystream", "mygroup")
    _ <- IO.println(s"Pending summary: ${summary.toOption}")
  yield ()
}
```

## Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| XADD | `xadd(key, fieldValues)` | `F[ValkeyResponse[String]]` |
| XLEN | `xlen(key)` | `F[ValkeyResponse[Long]]` |
| XDEL | `xdel(key, ids*)` | `F[ValkeyResponse[Long]]` |
| XTRIM | `xtrim(key, strategy)` | `F[ValkeyResponse[Long]]` |
| XRANGE | `xrange(key, start, end)` | `F[ValkeyResponse[Map[String, ...]]]` |
| XREVRANGE | `xrevrange(key, end, start)` | `F[ValkeyResponse[Map[String, ...]]]` |
| XREAD | `xread(keysAndIds)` | `F[ValkeyResponse[Option[Map[K, ...]]]]` |
| XGROUP CREATE | `xgroupCreate(key, group, id)` | `F[ValkeyResponse[Unit]]` |
| XGROUP DESTROY | `xgroupDestroy(key, group)` | `F[ValkeyResponse[Boolean]]` |
| XREADGROUP | `xreadgroup(group, consumer, keysAndIds)` | `F[ValkeyResponse[Option[Map[K, ...]]]]` |
| XACK | `xack(key, group, ids*)` | `F[ValkeyResponse[Long]]` |
| XCLAIM | `xclaim(key, group, consumer, minIdle, ids*)` | `F[ValkeyResponse[Map[String, ...]]]` |
| XPENDING | `xpendingSummary(key, group)` | `F[ValkeyResponse[PendingSummary[K]]]` |
| XAUTOCLAIM | `xautoclaim(key, group, consumer, minIdle, start)` | `F[ValkeyResponse[AutoClaimResult[K, V]]]` |
