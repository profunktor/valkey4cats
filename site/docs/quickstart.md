---
layout: docs
title:  "Quick Start"
number: 1
position: 1
---

# Quick Start

```scala mdoc:compile-only
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

This is the simplest way to get up and running with a single-node Valkey connection. Every command returns `F[ValkeyResponse[A]]` -- you pattern match on `Ok` / `Err` to handle domain-level results.

To learn more about commands, clustering, and error handling, please have a look at the extensive documentation.

You can continue reading about the different ways of acquiring a client and a connection [here](client.md).
