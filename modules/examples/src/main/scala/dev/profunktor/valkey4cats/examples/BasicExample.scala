package dev.profunktor.valkey4cats.examples

import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}

/** Basic usage example for Valkey4S
  *
  * To run this example, ensure you have Valkey or Redis running on localhost:6379
  *
  * Every command returns F[ValkeyResponse[A]] where:
  *   - ValkeyResponse.Ok(value) — command succeeded
  *   - ValkeyResponse.Err(error) — domain-level error (WRONGTYPE, READONLY, etc.)
  *   - Infrastructure errors (timeout, connection lost) propagate in F
  */
object BasicExample extends IOApp.Simple {

  implicit val logger: Log[IO] = Log.Stdout.instance[IO]

  def run: IO[Unit] = {
    Valkey[IO].utf8("redis://localhost:6379").use { valkey =>
      for {
        // Connection health check
        _ <- IO.println("=== Connection ===")
        pong <- valkey.ping
        _ <- IO.println(s"PING: ${pong.toOption.getOrElse("failed")}")

        // Basic SET/GET — pattern match on ValkeyResponse
        _ <- IO.println("\n=== String Operations ===")
        _ <- valkey.set("mykey", "Hello, Valkey4S!")
        value <- valkey.get("mykey")
        _ <- value match {
          case Ok(Some(v)) => IO.println(s"GET mykey: $v")
          case Ok(None)    => IO.println("GET mykey: (not found)")
          case Err(e)      => IO.println(s"GET mykey failed: ${e.message}")
        }

        // MSET/MGET
        _ <- IO.println("\n=== Multi-key Operations ===")
        _ <- valkey.mSet(
          Map(
            "user:1:name" -> "Alice",
            "user:2:name" -> "Bob",
            "user:3:name" -> "Charlie"
          )
        )
        users <- valkey.mGet(Set("user:1:name", "user:2:name", "user:3:name"))
        _ <- IO.println(s"Users: ${users.toOption.getOrElse(Map.empty)}")

        // INCR/DECR — use fold for concise handling
        _ <- IO.println("\n=== Counter Operations ===")
        _ <- valkey.set("counter", "0")
        c1 <- valkey.incr("counter")
        _ <- IO.println(
          s"After INCR: ${c1.fold(e => s"error: ${e.message}", _.toString)}"
        )
        c2 <- valkey.incrBy("counter", 10)
        _ <- IO.println(
          s"After INCRBY 10: ${c2.fold(e => s"error: ${e.message}", _.toString)}"
        )

        // Hash operations
        _ <- IO.println("\n=== Hash Operations ===")
        _ <- valkey.hset(
          "user:1",
          Map("name" -> "Alice", "email" -> "alice@example.com", "age" -> "30")
        )
        name <- valkey.hget("user:1", "name")
        _ <- IO.println(s"User name: ${name.toOption.flatten.getOrElse("?")}")
        allFields <- valkey.hgetall("user:1")
        _ <- IO.println(
          s"All fields: ${allFields.toOption.getOrElse(Map.empty)}"
        )

        // Key operations — expire, TTL, type
        _ <- IO.println("\n=== Key Management ===")
        _ <- valkey.expire("user:1", 3600)
        ttl <- valkey.ttl("user:1")
        _ <- IO.println(s"TTL on user:1: ${ttl.toOption.getOrElse(-1)}s")
        keyType <- valkey.typeOf("user:1")
        _ <- IO.println(
          s"Type of user:1: ${keyType.toOption.getOrElse("unknown")}"
        )

        // SCAN — iterate keys by pattern
        _ <- IO.println("\n=== Key Scanning ===")
        scanResult <- valkey.scan("0", "user:*", 100)
        _ <- scanResult match {
          case Ok(r) =>
            IO.println(s"Found keys: ${r.values.mkString(", ")}")
          case Err(e) => IO.println(s"SCAN failed: ${e.message}")
        }

        // Server info
        _ <- IO.println("\n=== Server ===")
        time <- valkey.time
        _ <- IO.println(
          s"Server time: ${time.toOption.map(_.unixSeconds).getOrElse(0)}s"
        )
        dbSize <- valkey.dbSize
        _ <- IO.println(s"DB size: ${dbSize.toOption.getOrElse(0)} keys")

        // Cleanup
        _ <- valkey.del(
          "mykey",
          "counter",
          "user:1",
          "user:1:name",
          "user:2:name",
          "user:3:name"
        )
        _ <- IO.println("\n=== Example completed successfully! ===")
      } yield ()
    }
  }
}
