---
layout: docs
title:  "Sorted Sets"
number: 11
---

# Sorted Sets API

Purely functional interface for the [Sorted Sets API](https://valkey.io/commands/#sorted-set).

Sorted sets are collections of unique string elements where each element has an associated score. Elements are ordered by score.

### Sorted Set Commands usage

Once you have acquired a connection you can start using it:

```scala mdoc:compile-only
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

    // ZRANK (ascending)
    rank <- valkey.zrank("leaderboard", "bob")
    _ <- IO.println(s"Bob's rank: ${rank.toOption}") // Some(Some(0)) - lowest score

    // ZRANGE (by index)
    top <- valkey.zrange("leaderboard", 0, -1)
    _ <- IO.println(s"All (ascending): ${top.toOption}")

    // ZRANGE with scores
    withScores <- valkey.zrangeWithScores("leaderboard", 0, -1)
    _ <- IO.println(s"With scores: ${withScores.toOption}")

    // ZINCRBY
    newScore <- valkey.zincrby("leaderboard", 20.0, "bob")
    _ <- IO.println(s"Bob's new score: ${newScore.toOption}") // Some(105.0)

    // ZCARD
    count <- valkey.zcard("leaderboard")
    _ <- IO.println(s"Members: ${count.toOption}") // Some(3)

    // ZCOUNT - count members in score range
    inRange <- valkey.zcount("leaderboard", 90.0, 110.0)
    _ <- IO.println(s"Scores 90-110: ${inRange.toOption}") // Some(2)

    // ZPOPMIN / ZPOPMAX
    lowest <- valkey.zpopmin("leaderboard")
    _ <- IO.println(s"Lowest: ${lowest.toOption}") // Some(Some(("carol", 92.0)))

    // ZREM
    _ <- valkey.zrem("leaderboard", "alice")

    // ZREMRANGEBYSCORE
    _ <- valkey.zremrangebyscore("leaderboard",
      ScoreBoundary.Score(0),
      ScoreBoundary.Score(50)
    )

    // Set operations: ZUNION, ZINTER, ZDIFF
    _ <- valkey.zadd("z1", Map("a" -> 1.0, "b" -> 2.0))
    _ <- valkey.zadd("z2", Map("b" -> 3.0, "c" -> 4.0))
    _ <- valkey.zunionstore("zunion_result", "z1", "z2")
    _ <- valkey.zinterstore("zinter_result", "z1", "z2")
  yield ()
}
```

### Blocking operations

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.arguments.ScoreFilter

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // BZPOPMIN - blocking pop of lowest score (5 second timeout)
    result <- valkey.bzpopmin(List("priority_queue"), 5.0)
    _ <- result.toOption.flatten match
      case Some((key, member, score)) =>
        IO.println(s"Got $member (score=$score) from $key")
      case None =>
        IO.println("Timeout")

    // ZMPOP - pop from first non-empty sorted set
    popped <- valkey.zmpop(List("q1", "q2"), ScoreFilter.Min)
    _ <- IO.println(s"Popped: ${popped.toOption}")
  yield ()
}
```

### Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| ZADD | `zadd(key, membersScores)` | `F[ValkeyResponse[Long]]` |
| ZADD (opts) | `zadd(key, membersScores, options)` | `F[ValkeyResponse[Long]]` |
| ZADD INCR | `zaddIncr(key, member, score)` | `F[ValkeyResponse[Option[Double]]]` |
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
| ZPOPMIN (count) | `zpopminCount(key, count)` | `F[ValkeyResponse[List[(V, Double)]]]` |
| ZPOPMAX | `zpopmax(key)` | `F[ValkeyResponse[Option[(V, Double)]]]` |
| ZPOPMAX (count) | `zpopmaxCount(key, count)` | `F[ValkeyResponse[List[(V, Double)]]]` |
| ZRANDMEMBER | `zrandmember(key)` | `F[ValkeyResponse[Option[V]]]` |
| ZRANDMEMBER (count) | `zrandmemberCount(key, count)` | `F[ValkeyResponse[List[V]]]` |
| ZRANDMEMBER (scores) | `zrandmemberWithScores(key, count)` | `F[ValkeyResponse[List[(V, Double)]]]` |
| ZREMRANGEBYRANK | `zremrangebyrank(key, start, stop)` | `F[ValkeyResponse[Long]]` |
| ZREMRANGEBYSCORE | `zremrangebyscore(key, min, max)` | `F[ValkeyResponse[Long]]` |
| ZDIFF | `zdiff(keys*)` | `F[ValkeyResponse[List[V]]]` |
| ZDIFFSTORE | `zdiffstore(dest, keys*)` | `F[ValkeyResponse[Long]]` |
| ZUNION | `zunion(keys*)` | `F[ValkeyResponse[List[V]]]` |
| ZUNIONSTORE | `zunionstore(dest, keys*)` | `F[ValkeyResponse[Long]]` |
| ZINTER | `zinter(keys*)` | `F[ValkeyResponse[List[V]]]` |
| ZINTERSTORE | `zinterstore(dest, keys*)` | `F[ValkeyResponse[Long]]` |
| ZINTERCARD | `zintercard(keys*)` | `F[ValkeyResponse[Long]]` |
| ZLEXCOUNT | `zlexcount(key, min, max)` | `F[ValkeyResponse[Long]]` |
| ZRANGESTORE | `zrangestore(dest, src, rangeQuery)` | `F[ValkeyResponse[Long]]` |
| ZMPOP | `zmpop(keys, filter)` | `F[ValkeyResponse[Option[(K, List[(V, Double)])]]]` |
| BZPOPMIN | `bzpopmin(keys, timeout)` | `F[ValkeyResponse[Option[(K, V, Double)]]]` |
| BZPOPMAX | `bzpopmax(keys, timeout)` | `F[ValkeyResponse[Option[(K, V, Double)]]]` |
| BZMPOP | `bzmpop(keys, filter, timeout)` | `F[ValkeyResponse[Option[(K, List[(V, Double)])]]]` |
| ZSCAN | `zscan(key, cursor)` | `F[ValkeyResponse[ScanResult[List[(V, Double)]]]]` |
