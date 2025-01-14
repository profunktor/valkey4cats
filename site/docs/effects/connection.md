---
layout: docs
title:  "Connection"
number: 14
---

# Connection API

Purely functional interface for the [Connection API](https://valkey.io/commands/#connection).

### Connection Commands usage

Once you have acquired a connection you can start using it:

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // PING
    pong <- valkey.ping
    _ <- IO.println(s"PING: ${pong.toOption}") // Some("PONG")

    // PING with message
    echo <- valkey.ping("hello")
    _ <- IO.println(s"PING hello: ${echo.toOption}") // Some("hello")

    // ECHO
    echoed <- valkey.echo("test message")
    _ <- IO.println(s"ECHO: ${echoed.toOption}") // Some("test message")

    // CLIENT ID
    clientId <- valkey.clientId
    _ <- IO.println(s"Client ID: ${clientId.toOption}")

    // CLIENT GETNAME
    name <- valkey.clientGetName
    _ <- IO.println(s"Client name: ${name.toOption}") // Some(None) initially

    // SELECT (switch database)
    _ <- valkey.select(1) // switch to database 1
    _ <- valkey.select(0) // switch back to database 0
  yield ()
}
```

### Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| PING | `ping` | `F[ValkeyResponse[String]]` |
| PING (msg) | `ping(message)` | `F[ValkeyResponse[V]]` |
| ECHO | `echo(message)` | `F[ValkeyResponse[V]]` |
| CLIENT ID | `clientId` | `F[ValkeyResponse[Long]]` |
| CLIENT GETNAME | `clientGetName` | `F[ValkeyResponse[Option[String]]]` |
| SELECT | `select(index)` | `F[ValkeyResponse[Unit]]` |
