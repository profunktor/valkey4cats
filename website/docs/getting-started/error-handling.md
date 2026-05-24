---
sidebar_position: 4
title: Error Handling
---

# Error Handling

A key difference between Valkey4Cats and other Redis/Valkey clients: **every command returns `F[ValkeyResponse[A]]`** rather than `F[A]`. This gives you explicit, typed control over domain-level errors without conflating them with infrastructure failures.

## ValkeyResponse

`ValkeyResponse[A]` is a sealed trait with two cases:

```scala
sealed trait ValkeyResponse[+A]

object ValkeyResponse:
  case class Ok[A](value: A)          extends ValkeyResponse[A]
  case class Err(error: ValkeyError)   extends ValkeyResponse[Nothing]
```

- **`Ok(value)`** — The command succeeded. The `value` is the decoded result.
- **`Err(error)`** — A domain-level error from the Valkey server (e.g., WRONGTYPE, READONLY, OOM).

Infrastructure errors (connection timeouts, network failures) are **not** captured in `ValkeyResponse` — they propagate naturally in `F` via `MonadError`.

## ValkeyError variants

Valkey4Cats parses server error prefixes into a typed ADT:

| Variant | Server prefix | Meaning |
|---------|---------------|---------|
| `WrongType` | WRONGTYPE | Operation against wrong data type |
| `CommandError` | ERR | General command failure |
| `ReadOnly` | READONLY | Write to a read-only replica |
| `CrossSlot` | CROSSSLOT | Multi-key op spanning cluster slots |
| `OutOfMemory` | OOM | Server memory limit reached |
| `AuthError` | NOAUTH / WRONGPASS | Authentication failure |
| `NoScript` | NOSCRIPT | Lua script not in cache |
| `Busy` | BUSY | Server busy (script running) |
| `TransactionAborted` | EXECABORT | WATCH key modified |
| `Unexpected` | (other) | Unrecognized error |

## Pattern matching

The most explicit way to handle responses:

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}
import dev.profunktor.valkey4cats.model.ValkeyError

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    result <- valkey.get("mykey")
    _ <- result match
      case Ok(Some(value)) => IO.println(s"Found: $value")
      case Ok(None)        => IO.println("Key not found")
      case Err(ValkeyError.WrongType(msg)) =>
        IO.println(s"Wrong type: $msg")
      case Err(other) =>
        IO.println(s"Unexpected error: ${other.message}")
  yield ()
}
```

## Using fold

For concise inline handling:

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    count <- valkey.incr("counter")
    _ <- IO.println(
      count.fold(
        err => s"Failed: ${err.message}",
        n   => s"Counter is now: $n"
      )
    )
  yield ()
}
```

## Converting to Option / Either

```scala
import dev.profunktor.valkey4cats.model.ValkeyResponse

val response: ValkeyResponse[String] = ValkeyResponse.ok("hello")

val opt: Option[String] = response.toOption       // Some("hello")
val either = response.toEither                     // Right("hello")
val withDefault: String = response.getOrElse("fallback")
```

## Lifting into F (liftTo)

If you prefer to treat domain errors as exceptions in `F` (similar to how other clients work), use `liftTo`:

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    value <- valkey.get("mykey").flatMap(_.liftTo[IO, Option[String]])
    _ <- IO.println(s"Got: $value")
  yield ()
}
```

## Cats type class instances

`ValkeyResponse` has instances for `Functor`, `Monad`, `MonadError[ValkeyResponse, ValkeyError]`, and `Traverse`. This means you can use standard Cats combinators:

```scala
import cats.syntax.all.*
import dev.profunktor.valkey4cats.model.ValkeyResponse

val r1: ValkeyResponse[Int] = ValkeyResponse.ok(42)
val r2: ValkeyResponse[Int] = ValkeyResponse.ok(58)

val sum = for
  a <- r1
  b <- r2
yield a + b
// Ok(100)
```

## Design rationale

This two-layer error model separates concerns cleanly:

| Layer | Captured in | Examples |
|-------|-------------|----------|
| Domain errors | `ValkeyResponse.Err` | WRONGTYPE, OOM, READONLY |
| Infrastructure errors | `F` (MonadError) | Connection timeout, network partition |

Your application logic can handle domain errors explicitly (pattern match, fold, recover) without `try/catch` style exception handling, while truly exceptional infrastructure failures remain in the effect type where they belong.
