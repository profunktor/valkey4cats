---
sidebar_position: 7
title: Sorted Sets
---

# Sorted Sets API

Purely functional interface for the [Sorted Sets API](https://valkey.io/commands/#sorted-set).

Sorted sets are collections of unique string elements where each element has an associated score. Elements are ordered by score.

## Usage

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}
import dev.profunktor.valkey4cats.arguments.ScoreBoundary

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // ZADD
    added <- valkey.zadd("leaderboard", Map(
      "alice" -> 100.0,
      "bob"   -> 85.0,
      "carol" -> 92.0
    ))
    _ <- IO.println(s"Added: ${added.toOption}") // Some(3)

    // ZSCORE
    score <- valkey.zscore("leaderboard", "alice")
    _ <- IO.println(s"Alice's score: ${score.toOption}") // Some(Some(100.0))

    // ZRANGE (by index)
    top <- valkey.zrange("leaderboard", 0, -1)
    _ <- IO.println(s"All (ascending): ${top.toOption}")

    // ZINCRBY
    newScore <- valkey.zincrby("leaderboard", 20.0, "bob")
    _ <- IO.println(s"Bob's new score: ${newScore.toOption}") // Some(105.0)

    // ZPOPMIN / ZPOPMAX
    lowest <- valkey.zpopmin("leaderboard")
    _ <- IO.println(s"Lowest: ${lowest.toOption}")

    // Set operations
    _ <- valkey.zadd("z1", Map("a" -> 1.0, "b" -> 2.0))
    _ <- valkey.zadd("z2", Map("b" -> 3.0, "c" -> 4.0))
    _ <- valkey.zunionstore("zunion_result", "z1", "z2")
  yield ()
}
```

## Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| ZADD | `zadd(key, membersScores)` | `F[ValkeyResponse[Long]]` |
| ZREM | `zrem(key, members*)` | `F[ValkeyResponse[Long]]` |
| ZRANGE | `zrange(key, start, stop)` | `F[ValkeyResponse[List[V]]]` |
| ZRANGE WITHSCORES | `zrangeWithScores(key, start, stop)` | `F[ValkeyResponse[List[(V, Double)]]]` |
| ZSCORE | `zscore(key, member)` | `F[ValkeyResponse[Option[Double]]]` |
| ZMSCORE | `zmscore(key, members*)` | `F[ValkeyResponse[List[Option[Double]]]]` |
| ZCARD | `zcard(key)` | `F[ValkeyResponse[Long]]` |
| ZRANK | `zrank(key, member)` | `F[ValkeyResponse[Option[Long]]]` |
| ZREVRANK | `zrevrank(key, member)` | `F[ValkeyResponse[Option[Long]]]` |
| ZINCRBY | `zincrby(key, increment, member)` | `F[ValkeyResponse[Double]]` |
| ZCOUNT | `zcount(key, min, max)` | `F[ValkeyResponse[Long]]` |
| ZPOPMIN | `zpopmin(key)` | `F[ValkeyResponse[Option[(V, Double)]]]` |
| ZPOPMAX | `zpopmax(key)` | `F[ValkeyResponse[Option[(V, Double)]]]` |
| ZRANDMEMBER | `zrandmember(key)` | `F[ValkeyResponse[Option[V]]]` |
| ZREMRANGEBYRANK | `zremrangebyrank(key, start, stop)` | `F[ValkeyResponse[Long]]` |
| ZREMRANGEBYSCORE | `zremrangebyscore(key, min, max)` | `F[ValkeyResponse[Long]]` |
| ZDIFF | `zdiff(keys*)` | `F[ValkeyResponse[List[V]]]` |
| ZUNION | `zunion(keys*)` | `F[ValkeyResponse[List[V]]]` |
| ZUNIONSTORE | `zunionstore(dest, keys*)` | `F[ValkeyResponse[Long]]` |
| ZINTER | `zinter(keys*)` | `F[ValkeyResponse[List[V]]]` |
| ZINTERSTORE | `zinterstore(dest, keys*)` | `F[ValkeyResponse[Long]]` |
| ZINTERCARD | `zintercard(keys*)` | `F[ValkeyResponse[Long]]` |
| ZMPOP | `zmpop(keys, filter)` | `F[ValkeyResponse[Option[(K, List[(V, Double)])]]]` |
| BZPOPMIN | `bzpopmin(keys, timeout)` | `F[ValkeyResponse[Option[(K, V, Double)]]]` |
| BZPOPMAX | `bzpopmax(keys, timeout)` | `F[ValkeyResponse[Option[(K, V, Double)]]]` |
| ZSCAN | `zscan(key, cursor)` | `F[ValkeyResponse[ScanResult[List[(V, Double)]]]]` |
