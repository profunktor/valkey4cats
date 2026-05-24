---
sidebar_position: 2
title: Client Configuration
---

# Valkey Client

The `Valkey[F]` entry point provides various constructors for creating Valkey connections. All constructors return a `Resource[F, ValkeyCommands[F, K, V]]` that manages the connection lifecycle.

Under the hood, Valkey4Cats uses the [Valkey Glide](https://github.com/valkey-io/valkey-glide) Java client.

## Simple connection

For the simplest use case, use `utf8` which gives you a `String`-keyed, `String`-valued connection:

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]

val commands = Valkey[IO].utf8("valkey://localhost:6379")
// Resource[IO, ValkeyCommands[IO, String, String]]
```

Or connect to localhost with defaults:

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]

val commands = Valkey[IO].localhost
// Equivalent to Valkey[IO].utf8("valkey://localhost:6379")
```

## Custom codecs

If you need non-String key or value types, use `simple` with implicit `Codec` instances:

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]

// Uses the built-in Codec[String] and Codec[Long]
val longCommands = Valkey[IO].simple[String, Long]("valkey://localhost:6379")
```

## Connection from configuration

For full control over client settings (TLS, credentials, timeouts, etc.), create a `ValkeyClientConfig`:

```scala
import cats.effect.*
import com.comcast.ip4s.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.*

given Log[IO] = Log.Stdout.instance[IO]

val commands = ValkeyClientConfig(
  addresses = List(NodeAddress(host"valkey.example.com", port"6380")),
  credentials = Some(ServerCredentials.password("secret")),
  tlsMode = TlsMode.Enabled()
) match
  case Right(cfg) => Valkey[IO].fromConfig[String, String](cfg)
  case Left(err)  => Resource.raiseError[IO, Nothing, Throwable](
    new IllegalArgumentException(err)
  )
```

## Cluster connection

Connect to a Valkey cluster by providing seed node URIs:

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]

val clusterCommands = Valkey[IO].clusterUtf8(
  "valkey://node1:6379",
  "valkey://node2:6379",
  "valkey://node3:6379"
)
```

Or with a `ValkeyClusterConfig` for full control:

```scala
import cats.effect.*
import com.comcast.ip4s.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.*

given Log[IO] = Log.Stdout.instance[IO]

val clusterCommands = ValkeyClusterConfig(
  addresses = List(
    NodeAddress(host"node1", port"6379"),
    NodeAddress(host"node2", port"6379"),
    NodeAddress(host"node3", port"6379")
  )
) match
  case Right(cfg) => Valkey[IO].fromClusterConfig[String, String](cfg)
  case Left(err)  => Resource.raiseError[IO, Nothing, Throwable](
    new IllegalArgumentException(err)
  )
```

## URI formats

Valkey4Cats supports the following URI schemes:

| Scheme | Description |
|--------|-------------|
| `redis://` | Standard connection (legacy compatibility) |
| `rediss://` | TLS connection (legacy compatibility) |
| `valkey://` | Native Valkey standard connection |
| `valkeys://` | Native Valkey TLS connection |

Examples:

```
valkey://localhost:6379
valkeys://secure-server:6380
valkey://:password@localhost:6379
valkey://username:password@localhost:6379/2
```

## Logger

In order to create a client and connection you must provide a `Log` instance.

### Disable logging

```scala
import cats.effect.IO
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.NoOp.instance[IO]
```

### Console logging

```scala
import cats.effect.IO
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]
```

### log4cats integration

For structured logging, use the `valkey4cats-log4cats` module:

```scala
import cats.effect.IO
import dev.profunktor.valkey4cats.log4cats.given
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

given Logger[IO] = Slf4jLogger.getLogger[IO]
// Log[IO] is now derived automatically
```
