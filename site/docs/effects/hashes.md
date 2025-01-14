---
layout: docs
title:  "Hashes"
number: 6
---

# Hashes API

Purely functional interface for the [Hashes API](https://valkey.io/commands/#hash).

Hashes are maps between string fields and string values, making them perfect for representing objects.

### Hash Commands usage

Once you have acquired a connection you can start using it:

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // HSET - set multiple fields
    _ <- valkey.hset("user:1", Map(
      "name"  -> "Alice",
      "email" -> "alice@example.com",
      "age"   -> "30"
    ))

    // HGET - get a single field
    name <- valkey.hget("user:1", "name")
    _ <- name match
      case Ok(Some(v)) => IO.println(s"Name: $v")
      case Ok(None)    => IO.println("Field not found")
      case Err(e)      => IO.println(s"Error: ${e.message}")

    // HGETALL - get all fields and values
    all <- valkey.hgetall("user:1")
    _ <- IO.println(s"All fields: ${all.toOption}")

    // HMGET - get multiple fields
    fields <- valkey.hmget("user:1", "name", "email")
    _ <- IO.println(s"Fields: ${fields.toOption}")

    // HSETNX - set only if field doesn't exist
    wasSet <- valkey.hsetnx("user:1", "name", "should not happen")
    _ <- IO.println(s"HSETNX: ${wasSet.toOption}") // Some(false)

    // HDEL - delete fields
    deleted <- valkey.hdel("user:1", "age")
    _ <- IO.println(s"Deleted fields: ${deleted.toOption}") // Some(1)

    // HEXISTS
    exists <- valkey.hexists("user:1", "name")
    _ <- IO.println(s"Field exists: ${exists.toOption}") // Some(true)

    // HKEYS / HVALS / HLEN
    keys <- valkey.hkeys("user:1")
    _ <- IO.println(s"Keys: ${keys.toOption}")
    vals <- valkey.hvals("user:1")
    _ <- IO.println(s"Values: ${vals.toOption}")
    len <- valkey.hlen("user:1")
    _ <- IO.println(s"Length: ${len.toOption}")

    // HINCRBY
    _ <- valkey.hset("user:1", Map("score" -> "100"))
    newScore <- valkey.hincrBy("user:1", "score", 50)
    _ <- IO.println(s"New score: ${newScore.toOption}") // Some(150)
  yield ()
}
```

### Hash field expiration (Valkey 8.0+)

Valkey4Cats supports per-field expiration on hashes, a feature introduced in Valkey 8.0:

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    _ <- valkey.hset("session:abc", Map("token" -> "xyz", "user" -> "alice"))
    // Set 60-second TTL on the token field
    _ <- valkey.hexpire("session:abc", 60, "token")
    // Check remaining TTL
    ttl <- valkey.httl("session:abc", "token")
    _ <- IO.println(s"Token TTL: ${ttl.toOption}")
  yield ()
}
```

### Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| HSET | `hset(key, fieldValues)` | `F[ValkeyResponse[Long]]` |
| HGET | `hget(key, field)` | `F[ValkeyResponse[Option[V]]]` |
| HGETALL | `hgetall(key)` | `F[ValkeyResponse[Map[K, V]]]` |
| HMGET | `hmget(key, fields*)` | `F[ValkeyResponse[List[Option[V]]]]` |
| HDEL | `hdel(key, fields*)` | `F[ValkeyResponse[Long]]` |
| HEXISTS | `hexists(key, field)` | `F[ValkeyResponse[Boolean]]` |
| HKEYS | `hkeys(key)` | `F[ValkeyResponse[List[K]]]` |
| HVALS | `hvals(key)` | `F[ValkeyResponse[List[V]]]` |
| HLEN | `hlen(key)` | `F[ValkeyResponse[Long]]` |
| HINCRBY | `hincrBy(key, field, increment)` | `F[ValkeyResponse[Long]]` |
| HINCRBYFLOAT | `hincrByFloat(key, field, increment)` | `F[ValkeyResponse[Double]]` |
| HSETNX | `hsetnx(key, field, value)` | `F[ValkeyResponse[Boolean]]` |
| HSTRLEN | `hstrlen(key, field)` | `F[ValkeyResponse[Long]]` |
| HRANDFIELD | `hrandfield(key)` | `F[ValkeyResponse[Option[K]]]` |
| HRANDFIELD (count) | `hrandfieldWithCount(key, count)` | `F[ValkeyResponse[List[K]]]` |
| HRANDFIELD (with values) | `hrandfieldWithCountWithValues(key, count)` | `F[ValkeyResponse[Map[K, V]]]` |
| HSCAN | `hscan(key, cursor)` | `F[ValkeyResponse[ScanResult[...]]]` |
| HEXPIRE | `hexpire(key, seconds, fields*)` | `F[ValkeyResponse[List[Long]]]` |
| HPEXPIRE | `hpexpire(key, millis, fields*)` | `F[ValkeyResponse[List[Long]]]` |
| HTTL | `httl(key, fields*)` | `F[ValkeyResponse[List[Long]]]` |
| HPTTL | `hpttl(key, fields*)` | `F[ValkeyResponse[List[Long]]]` |
| HPERSIST | `hpersist(key, fields*)` | `F[ValkeyResponse[List[Long]]]` |
| HGETEX | `hgetex(key, expiry, fields*)` | `F[ValkeyResponse[List[Option[V]]]]` |
| HSETEX | `hsetex(key, fieldValues, expiry)` | `F[ValkeyResponse[Long]]` |
