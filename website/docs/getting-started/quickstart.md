---
sidebar_position: 1
title: Quick Start
---

# Quick Start

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}

object QuickStart extends IOApp.Simple:

  given Log[IO] = Log.Stdout.instance[IO]

  def run: IO[Unit] =
    Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
      for
        _ <- valkey.set("foo", "123")
        x <- valkey.get("foo")
        _ <- x match
          case Ok(Some(v)) => IO.println(s"Got: $v")
          case Ok(None)    => IO.println("Not found")
          case Err(e)      => IO.println(s"Error: ${e.message}")
      yield ()
    }
```

This is the simplest way to get up and running with a single-node Valkey connection. Every command returns `F[ValkeyResponse[A]]` — you pattern match on `Ok` / `Err` to handle domain-level results.

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.profunktor" %% "valkey4cats-effects" % "<version>"
```

For structured logging, add the optional log4cats integration:

```scala
libraryDependencies += "dev.profunktor" %% "valkey4cats-log4cats" % "<version>"
```

## Requirements

- Scala 3.5+
- Java 11+ (Valkey Glide requires JDK 11 or later)
- A running Valkey server (or use Docker: `docker run -p 6379:6379 valkey/valkey:8`)

## Next steps

- [Client Configuration](client.md) — connection types, URIs, TLS, clustering
- [Codecs](codecs.md) — custom key/value types
- [Error Handling](error-handling.md) — the `ValkeyResponse` model
