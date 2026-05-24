---
sidebar_position: 13
title: Server
---

# Server API

Purely functional interface for the [Server API](https://valkey.io/commands/#server).

## Usage

```scala
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

    // DBSIZE
    dbSize <- valkey.dbSize
    _ <- IO.println(s"DB size: ${dbSize.toOption} keys")

    // CONFIG GET / SET
    config <- valkey.configGet(Set("maxmemory", "timeout"))
    _ <- IO.println(s"Config: ${config.toOption}")
    _ <- valkey.configSet(Map("timeout" -> "300"))

    // LOLWUT
    art <- valkey.lolwut
    _ <- IO.println(s"LOLWUT: ${art.toOption.map(_.take(50))}...")
  yield ()
}
```

## Available commands

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
| DBSIZE | `dbSize` | `F[ValkeyResponse[Long]]` |
