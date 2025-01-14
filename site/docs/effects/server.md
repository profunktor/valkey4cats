---
layout: docs
title:  "Server"
number: 15
---

# Server API

Purely functional interface for the [Server API](https://valkey.io/commands/#server).

### Server Commands usage

Once you have acquired a connection you can start using it:

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}
import dev.profunktor.valkey4cats.arguments.{FlushMode, InfoSection}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // INFO - get server information
    info <- valkey.info
    _ <- info match
      case Ok(infoStr) => IO.println(s"Server info length: ${infoStr.length}")
      case Err(e)      => IO.println(s"Error: ${e.message}")

    // INFO with specific sections
    memInfo <- valkey.info(Set(InfoSection.Memory))
    _ <- IO.println(s"Memory info: ${memInfo.toOption.map(_.take(100))}")

    // TIME - server time
    time <- valkey.time
    _ <- IO.println(s"Server time: ${time.toOption.map(_.unixSeconds)}s")

    // DBSIZE - number of keys
    dbSize <- valkey.dbSize
    _ <- IO.println(s"DB size: ${dbSize.toOption} keys")

    // CONFIG GET
    config <- valkey.configGet(Set("maxmemory", "timeout"))
    _ <- IO.println(s"Config: ${config.toOption}")

    // CONFIG SET
    _ <- valkey.configSet(Map("timeout" -> "300"))

    // LASTSAVE
    lastSave <- valkey.lastSave
    _ <- IO.println(s"Last save: ${lastSave.toOption}")

    // LOLWUT - display version art
    art <- valkey.lolwut
    _ <- IO.println(s"LOLWUT: ${art.toOption.map(_.take(50))}...")

    // FLUSHDB (use with caution!)
    // _ <- valkey.flushDB
    // _ <- valkey.flushDB(FlushMode.Async)

    // FLUSHALL (use with extreme caution!)
    // _ <- valkey.flushAll
    // _ <- valkey.flushAll(FlushMode.Async)
  yield ()
}
```

### Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| INFO | `info` | `F[ValkeyResponse[String]]` |
| INFO (sections) | `info(sections)` | `F[ValkeyResponse[String]]` |
| CONFIG REWRITE | `configRewrite` | `F[ValkeyResponse[Unit]]` |
| CONFIG RESETSTAT | `configResetStat` | `F[ValkeyResponse[Unit]]` |
| CONFIG GET | `configGet(parameters)` | `F[ValkeyResponse[Map[String, String]]]` |
| CONFIG SET | `configSet(parameters)` | `F[ValkeyResponse[Unit]]` |
| TIME | `time` | `F[ValkeyResponse[ServerTime]]` |
| LASTSAVE | `lastSave` | `F[ValkeyResponse[Long]]` |
| FLUSHALL | `flushAll` | `F[ValkeyResponse[Unit]]` |
| FLUSHALL (mode) | `flushAll(mode)` | `F[ValkeyResponse[Unit]]` |
| FLUSHDB | `flushDB` | `F[ValkeyResponse[Unit]]` |
| FLUSHDB (mode) | `flushDB(mode)` | `F[ValkeyResponse[Unit]]` |
| LOLWUT | `lolwut` | `F[ValkeyResponse[String]]` |
| LOLWUT (ver) | `lolwut(version)` | `F[ValkeyResponse[String]]` |
| LOLWUT (ver, params) | `lolwut(version, parameters)` | `F[ValkeyResponse[String]]` |
| DBSIZE | `dbSize` | `F[ValkeyResponse[Long]]` |
