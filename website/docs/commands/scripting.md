---
sidebar_position: 14
title: Scripting
---

# Scripting API

Purely functional interface for the [Scripting API](https://valkey.io/commands/#scripting).

Valkey4Cats supports server-side function execution via the `FCALL` command (Valkey Functions), as well as script cache management.

## Usage

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}
import dev.profunktor.valkey4cats.arguments.FlushMode

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // FCALL - call a server-side function
    result <- valkey.fcall("myfunc", List("key1"), List("arg1", "arg2"))
    _ <- result match
      case Ok(value) => IO.println(s"Function returned: $value")
      case Err(e)    => IO.println(s"Error: ${e.message}")

    // FCALL_RO - call a read-only function
    roResult <- valkey.fcallReadOnly("myreadfunc", List("key1"), Nil)
    _ <- IO.println(s"Read-only result: ${roResult.toOption}")

    // SCRIPT EXISTS - check if scripts are cached
    exists <- valkey.scriptExists("sha1abc123", "sha1def456")
    _ <- IO.println(s"Scripts exist: ${exists.toOption}")

    // SCRIPT FLUSH - clear the script cache
    _ <- valkey.scriptFlush(FlushMode.Async)
  yield ()
}
```

:::note
Unlike redis4cats which supports `EVAL` and `EVALSHA` directly, Valkey4Cats currently exposes the newer Valkey Functions API (`FCALL` / `FCALL_RO`). Functions are the recommended approach for server-side scripting in modern Valkey deployments.

To use functions, first load them using the `FUNCTION LOAD` command on the server, then call them by name via `fcall` or `fcallReadOnly`.
:::

## Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| FCALL | `fcall(function, keys, args)` | `F[ValkeyResponse[String]]` |
| FCALL_RO | `fcallReadOnly(function, keys, args)` | `F[ValkeyResponse[String]]` |
| SCRIPT FLUSH | `scriptFlush` | `F[ValkeyResponse[Unit]]` |
| SCRIPT FLUSH (mode) | `scriptFlush(mode)` | `F[ValkeyResponse[Unit]]` |
| SCRIPT KILL | `scriptKill` | `F[ValkeyResponse[Unit]]` |
| SCRIPT EXISTS | `scriptExists(sha1s*)` | `F[ValkeyResponse[List[Boolean]]]` |
