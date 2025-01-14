package dev.profunktor.valkey4cats.examples

import cats.effect.*
import com.comcast.ip4s.{host, port}
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.codec.Codec.utf8Codec
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.*
import dev.profunktor.valkey4cats.model.TlsMode.Disabled

import scala.concurrent.duration.*

/** Advanced configuration example showing various client options */
object ConfigExample extends IOApp.Simple {

  implicit val logger: Log[IO] = Log.Stdout.instance[IO]

  def run: IO[Unit] = {
    // Example 1: Simple URI parsing
    val simpleExample = Valkey[IO].utf8("redis://localhost:6379")

    // Example 2: URI with authentication
    val _ = Valkey[IO].utf8("redis://:mypassword@localhost:6379")

    // Example 3: TLS connection
    val _ = Valkey[IO].utf8("rediss://secure-server:6380")

    // Example 4: Full configuration with all options (using validated smart constructor)
    val config = ValkeyClientConfig(
      addresses = List(NodeAddress(host"localhost", port"6379")),
      tlsMode = Disabled,
      requestTimeout = Some(5.seconds),
      credentials = Some(ServerCredentials.password("mypassword")),
      readFrom = Some(ReadFromStrategy.Primary),
      reconnectStrategy = Some(
        BackOffStrategy.ExponentialBackoff(
          numOfRetries = 5,
          baseFactor = 100.millis,
          exponentBase = 2,
          jitterPercent = 10
        )
      ),
      databaseId = Some(DatabaseId.unsafe(0)),
      clientName = Some("my-app"),
      protocolVersion = ProtocolVersion.RESP3
    )

    config match {
      case Right(cfg) =>
        val _ = Valkey[IO].fromConfig[String, String](cfg)
      case Left(err) =>
        System.err.println(s"Config error: $err")
    }

    // Use the simple example
    simpleExample.use { valkey =>
      for {
        _ <- IO.println("=== Configuration Examples ===")
        _ <- valkey.set("config-test", "success")
        result <- valkey.get("config-test")
        _ <- IO.println(s"Result: ${result.getOrElse(None)}")
        _ <- valkey.del("config-test")
      } yield ()
    }
  }
}
