---
sidebar_position: 2
title: Strings
---

# Strings API

Purely functional interface for the [Strings API](https://valkey.io/commands/#string).

## Usage

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // SET and GET
    _ <- valkey.set("username", "gvolpe")
    x <- valkey.get("username")
    _ <- x match
      case Ok(Some(v)) => IO.println(s"Got: $v")
      case Ok(None)    => IO.println("Not found")
      case Err(e)      => IO.println(s"Error: ${e.message}")

    // SET with NX (only if not exists)
    nx <- valkey.setNx("username", "should not happen")
    _ <- IO.println(s"SetNx result: ${nx.toOption}")  // Ok(false)

    // INCR / DECR
    _ <- valkey.set("counter", "0")
    c1 <- valkey.incr("counter")
    _ <- IO.println(s"After INCR: ${c1.toOption}")    // Some(1)
    c2 <- valkey.incrBy("counter", 10)
    _ <- IO.println(s"After INCRBY 10: ${c2.toOption}") // Some(11)
    c3 <- valkey.decrBy("counter", 5)
    _ <- IO.println(s"After DECRBY 5: ${c3.toOption}")  // Some(6)

    // MSET / MGET
    _ <- valkey.mSet(Map("a" -> "1", "b" -> "2", "c" -> "3"))
    values <- valkey.mGet(Set("a", "b", "c"))
    _ <- IO.println(s"MGET: ${values.toOption}")

    // APPEND and STRLEN
    _ <- valkey.set("greeting", "Hello")
    len <- valkey.append("greeting", ", World!")
    _ <- IO.println(s"After APPEND, length: ${len.toOption}") // Some(13)

    // GETDEL
    deleted <- valkey.getDel("greeting")
    _ <- IO.println(s"GETDEL: ${deleted.toOption}") // Some(Some("Hello, World!"))
  yield ()
}
```

## Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| GET | `get(key)` | `F[ValkeyResponse[Option[V]]]` |
| SET | `set(key, value)` | `F[ValkeyResponse[Unit]]` |
| SET (with options) | `set(key, value, options)` | `F[ValkeyResponse[SetResult[V]]]` |
| SETNX | `setNx(key, value)` | `F[ValkeyResponse[Boolean]]` |
| MGET | `mGet(keys)` | `F[ValkeyResponse[Map[K, V]]]` |
| MSET | `mSet(keyValues)` | `F[ValkeyResponse[Unit]]` |
| MSETNX | `mSetNx(keyValues)` | `F[ValkeyResponse[Boolean]]` |
| INCR | `incr(key)` | `F[ValkeyResponse[Long]]` |
| INCRBY | `incrBy(key, amount)` | `F[ValkeyResponse[Long]]` |
| INCRBYFLOAT | `incrByFloat(key, amount)` | `F[ValkeyResponse[Double]]` |
| DECR | `decr(key)` | `F[ValkeyResponse[Long]]` |
| DECRBY | `decrBy(key, amount)` | `F[ValkeyResponse[Long]]` |
| APPEND | `append(key, value)` | `F[ValkeyResponse[Long]]` |
| STRLEN | `strlen(key)` | `F[ValkeyResponse[Long]]` |
| GETRANGE | `getRange(key, start, end)` | `F[ValkeyResponse[V]]` |
| SETRANGE | `setRange(key, offset, value)` | `F[ValkeyResponse[Long]]` |
| GETEX | `getEx(key, expiry)` | `F[ValkeyResponse[Option[V]]]` |
| GETDEL | `getDel(key)` | `F[ValkeyResponse[Option[V]]]` |
| LCS | `lcs(key1, key2)` | `F[ValkeyResponse[V]]` |
| LCS LEN | `lcsLen(key1, key2)` | `F[ValkeyResponse[Long]]` |
