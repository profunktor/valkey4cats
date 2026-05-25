---
sidebar_position: 4
title: Keys
---

# Keys API

Purely functional interface for the [Keys API](https://valkey.io/commands/#generic).

## Usage

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // DEL
    _ <- valkey.set("mykey", "hello")
    deleted <- valkey.del("mykey")
    _ <- IO.println(s"Deleted: ${deleted.toOption}") // Some(1)

    // EXISTS
    exists <- valkey.exists("mykey")
    _ <- IO.println(s"Exists: ${exists.toOption}") // Some(false)

    // EXPIRE and TTL
    _ <- valkey.set("ephemeral", "data")
    _ <- valkey.expire("ephemeral", 60)
    ttl <- valkey.ttl("ephemeral")
    _ <- IO.println(s"TTL: ${ttl.toOption}") // Some(60)

    // PERSIST (remove expiration)
    _ <- valkey.persist("ephemeral")

    // TYPE
    _ <- valkey.set("str_key", "value")
    keyType <- valkey.typeOf("str_key")
    _ <- IO.println(s"Type: ${keyType.toOption}") // Some("string")

    // RENAME
    _ <- valkey.rename("str_key", "new_key")

    // SCAN
    scanResult <- valkey.scan("0", "new*", 100)
    _ <- scanResult match
      case Ok(result) => IO.println(s"Found: ${result.values}")
      case Err(e)     => IO.println(s"Error: ${e.message}")

    // UNLINK (async DEL)
    _ <- valkey.unlink("new_key", "ephemeral")
  yield ()
}
```

## Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| DEL | `del(keys*)` | `F[ValkeyResponse[Long]]` |
| EXISTS | `exists(key)` | `F[ValkeyResponse[Boolean]]` |
| EXISTS (multi) | `existsMany(keys*)` | `F[ValkeyResponse[Long]]` |
| UNLINK | `unlink(keys*)` | `F[ValkeyResponse[Long]]` |
| EXPIRE | `expire(key, seconds)` | `F[ValkeyResponse[Boolean]]` |
| PEXPIRE | `pexpire(key, millis)` | `F[ValkeyResponse[Boolean]]` |
| EXPIREAT | `expireAt(key, unixSeconds)` | `F[ValkeyResponse[Boolean]]` |
| PEXPIREAT | `pexpireAt(key, unixMillis)` | `F[ValkeyResponse[Boolean]]` |
| TTL | `ttl(key)` | `F[ValkeyResponse[Long]]` |
| PTTL | `pttl(key)` | `F[ValkeyResponse[Long]]` |
| PERSIST | `persist(key)` | `F[ValkeyResponse[Boolean]]` |
| RENAME | `rename(key, newKey)` | `F[ValkeyResponse[Unit]]` |
| RENAMENX | `renameNx(key, newKey)` | `F[ValkeyResponse[Boolean]]` |
| TYPE | `typeOf(key)` | `F[ValkeyResponse[String]]` |
| TOUCH | `touch(keys*)` | `F[ValkeyResponse[Long]]` |
| COPY | `copy(source, dest)` | `F[ValkeyResponse[Boolean]]` |
| RANDOMKEY | `randomKey` | `F[ValkeyResponse[Option[K]]]` |
| SORT | `sort(key)` | `F[ValkeyResponse[List[V]]]` |
| DUMP | `dump(key)` | `F[ValkeyResponse[Option[Array[Byte]]]]` |
| RESTORE | `restore(key, ttl, bytes)` | `F[ValkeyResponse[Unit]]` |
| SCAN | `scan(cursor)` | `F[ValkeyResponse[ScanResult[List[K]]]]` |
| SCAN (match) | `scan(cursor, pattern, count)` | `F[ValkeyResponse[ScanResult[List[K]]]]` |
