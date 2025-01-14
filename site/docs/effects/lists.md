---
layout: docs
title:  "Lists"
number: 8
---

# Lists API

Purely functional interface for the [Lists API](https://valkey.io/commands/#list).

Lists are simple collections of string elements sorted by insertion order.

### List Commands usage

Once you have acquired a connection you can start using it:

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}
import dev.profunktor.valkey4cats.arguments.{InsertPosition, ListDirection}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // RPUSH / LPUSH
    _ <- valkey.rpush("mylist", "one", "two", "three")
    _ <- valkey.lpush("mylist", "zero")

    // LRANGE - get a range of elements
    range <- valkey.lrange("mylist", 0, -1)
    _ <- IO.println(s"Range: ${range.toOption}")
    // Some(List("zero", "one", "two", "three"))

    // LLEN
    len <- valkey.llen("mylist")
    _ <- IO.println(s"Length: ${len.toOption}") // Some(4)

    // LPOP / RPOP
    left <- valkey.lpop("mylist")
    _ <- IO.println(s"Left pop: ${left.toOption}") // Some(Some("zero"))
    right <- valkey.rpop("mylist")
    _ <- IO.println(s"Right pop: ${right.toOption}") // Some(Some("three"))

    // LINDEX
    elem <- valkey.lindex("mylist", 0)
    _ <- IO.println(s"Index 0: ${elem.toOption}") // Some(Some("one"))

    // LINSERT
    _ <- valkey.linsert("mylist", InsertPosition.Before, "two", "one-and-a-half")

    // LREM - remove occurrences
    removed <- valkey.lrem("mylist", 1, "one")
    _ <- IO.println(s"Removed: ${removed.toOption}") // Some(1)

    // LTRIM - trim to range
    _ <- valkey.ltrim("mylist", 0, 1)

    // LMOVE - atomic pop+push between lists
    _ <- valkey.rpush("source", "a", "b", "c")
    moved <- valkey.lmove("source", "dest", ListDirection.Right, ListDirection.Left)
    _ <- IO.println(s"Moved: ${moved.toOption}") // Some(Some("c"))
  yield ()
}
```

### Blocking operations

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.arguments.ListDirection

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // BLPOP - blocking left pop (5 second timeout)
    result <- valkey.blpop(List("queue1", "queue2"), 5.0)
    _ <- result.toOption.flatten match
      case Some((key, value)) => IO.println(s"Got $value from $key")
      case None               => IO.println("Timeout, no element available")

    // BLMOVE - blocking move between lists
    moved <- valkey.blmove("source", "dest", ListDirection.Left, ListDirection.Right, 5.0)
    _ <- IO.println(s"Blocking move result: ${moved.toOption}")
  yield ()
}
```

### Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| LPUSH | `lpush(key, elements*)` | `F[ValkeyResponse[Long]]` |
| RPUSH | `rpush(key, elements*)` | `F[ValkeyResponse[Long]]` |
| LPOP | `lpop(key)` | `F[ValkeyResponse[Option[V]]]` |
| RPOP | `rpop(key)` | `F[ValkeyResponse[Option[V]]]` |
| LPOP COUNT | `lpopCount(key, count)` | `F[ValkeyResponse[List[V]]]` |
| RPOP COUNT | `rpopCount(key, count)` | `F[ValkeyResponse[List[V]]]` |
| LRANGE | `lrange(key, start, stop)` | `F[ValkeyResponse[List[V]]]` |
| LINDEX | `lindex(key, index)` | `F[ValkeyResponse[Option[V]]]` |
| LLEN | `llen(key)` | `F[ValkeyResponse[Long]]` |
| LTRIM | `ltrim(key, start, stop)` | `F[ValkeyResponse[Unit]]` |
| LSET | `lset(key, index, element)` | `F[ValkeyResponse[Unit]]` |
| LREM | `lrem(key, count, element)` | `F[ValkeyResponse[Long]]` |
| LINSERT | `linsert(key, position, pivot, element)` | `F[ValkeyResponse[InsertResult]]` |
| LPOS | `lpos(key, element)` | `F[ValkeyResponse[Option[Long]]]` |
| LPOS COUNT | `lposCount(key, element, count)` | `F[ValkeyResponse[List[Long]]]` |
| LPUSHX | `lpushx(key, elements*)` | `F[ValkeyResponse[Long]]` |
| RPUSHX | `rpushx(key, elements*)` | `F[ValkeyResponse[Long]]` |
| LMOVE | `lmove(src, dst, from, to)` | `F[ValkeyResponse[Option[V]]]` |
| BLPOP | `blpop(keys, timeout)` | `F[ValkeyResponse[Option[(K, V)]]]` |
| BRPOP | `brpop(keys, timeout)` | `F[ValkeyResponse[Option[(K, V)]]]` |
| BLMOVE | `blmove(src, dst, from, to, timeout)` | `F[ValkeyResponse[Option[V]]]` |
| LMPOP | `lmpop(keys, direction)` | `F[ValkeyResponse[Option[(K, List[V])]]]` |
| BLMPOP | `blmpop(keys, direction, timeout)` | `F[ValkeyResponse[Option[(K, List[V])]]]` |
