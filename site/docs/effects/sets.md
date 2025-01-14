---
layout: docs
title:  "Sets"
number: 9
---

# Sets API

Purely functional interface for the [Sets API](https://valkey.io/commands/#set).

Sets are unordered collections of unique string elements.

### Set Commands usage

Once you have acquired a connection you can start using it:

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // SADD
    added <- valkey.sadd("myset", "hello", "world", "foo")
    _ <- IO.println(s"Added: ${added.toOption}") // Some(3)

    // SMEMBERS
    members <- valkey.smembers("myset")
    _ <- IO.println(s"Members: ${members.toOption}")

    // SISMEMBER
    isMember <- valkey.sismember("myset", "hello")
    _ <- IO.println(s"Is member: ${isMember.toOption}") // Some(true)

    // SCARD
    size <- valkey.scard("myset")
    _ <- IO.println(s"Size: ${size.toOption}") // Some(3)

    // SREM
    removed <- valkey.srem("myset", "foo")
    _ <- IO.println(s"Removed: ${removed.toOption}") // Some(1)

    // Set operations: SUNION, SINTER, SDIFF
    _ <- valkey.sadd("set1", "a", "b", "c")
    _ <- valkey.sadd("set2", "b", "c", "d")

    union <- valkey.sunion("set1", "set2")
    _ <- IO.println(s"Union: ${union.toOption}") // {a, b, c, d}

    inter <- valkey.sinter("set1", "set2")
    _ <- IO.println(s"Intersection: ${inter.toOption}") // {b, c}

    diff <- valkey.sdiff("set1", "set2")
    _ <- IO.println(s"Difference: ${diff.toOption}") // {a}

    // SPOP - random remove
    popped <- valkey.spop("myset")
    _ <- IO.println(s"Popped: ${popped.toOption}")

    // SRANDMEMBER - random without removing
    random <- valkey.srandmember("myset")
    _ <- IO.println(s"Random member: ${random.toOption}")

    // SMOVE - move member between sets
    _ <- valkey.smove("set1", "set2", "a")
  yield ()
}
```

### Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| SADD | `sadd(key, members*)` | `F[ValkeyResponse[Long]]` |
| SREM | `srem(key, members*)` | `F[ValkeyResponse[Long]]` |
| SMEMBERS | `smembers(key)` | `F[ValkeyResponse[Set[V]]]` |
| SISMEMBER | `sismember(key, member)` | `F[ValkeyResponse[Boolean]]` |
| SMISMEMBER | `smismember(key, members*)` | `F[ValkeyResponse[List[Boolean]]]` |
| SCARD | `scard(key)` | `F[ValkeyResponse[Long]]` |
| SUNION | `sunion(keys*)` | `F[ValkeyResponse[Set[V]]]` |
| SUNIONSTORE | `sunionstore(dest, keys*)` | `F[ValkeyResponse[Long]]` |
| SINTER | `sinter(keys*)` | `F[ValkeyResponse[Set[V]]]` |
| SINTERSTORE | `sinterstore(dest, keys*)` | `F[ValkeyResponse[Long]]` |
| SINTERCARD | `sintercard(keys*)` | `F[ValkeyResponse[Long]]` |
| SDIFF | `sdiff(keys*)` | `F[ValkeyResponse[Set[V]]]` |
| SDIFFSTORE | `sdiffstore(dest, keys*)` | `F[ValkeyResponse[Long]]` |
| SPOP | `spop(key)` | `F[ValkeyResponse[Option[V]]]` |
| SPOP COUNT | `spopCount(key, count)` | `F[ValkeyResponse[Set[V]]]` |
| SRANDMEMBER | `srandmember(key)` | `F[ValkeyResponse[Option[V]]]` |
| SRANDMEMBER COUNT | `srandmemberCount(key, count)` | `F[ValkeyResponse[List[V]]]` |
| SMOVE | `smove(src, dest, member)` | `F[ValkeyResponse[Boolean]]` |
| SSCAN | `sscan(key, cursor)` | `F[ValkeyResponse[ScanResult[Set[V]]]]` |
