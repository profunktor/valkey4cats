package dev.profunktor.valkey4cats.examples

import cats.effect.*
import com.comcast.ip4s.{host, port}
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.codec.Codec.utf8Codec
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.*

import scala.concurrent.duration.*

/** Cluster configuration example
  *
  * This demonstrates how to configure a Valkey cluster connection with various options
  */
object ClusterExample extends IOApp.Simple {

  implicit val logger: Log[IO] = Log.Stdout.instance[IO]

  def run: IO[Unit] = {
    // Build a cluster configuration using effectful smart constructor
    val configF = ValkeyClusterConfig.make[IO](
      addresses = List(
        NodeAddress(host"localhost", port"7000"),
        NodeAddress(host"localhost", port"7001"),
        NodeAddress(host"localhost", port"7002")
      ),
      requestTimeout = Some(2.seconds),
      readFrom = Some(ReadFromStrategy.PreferReplica),
      clientName = Some("valkey4s-example")
    )

    // Use the cluster (with UTF-8 string codec)
    Resource
      .eval(configF)
      .flatMap(config => Valkey[IO].fromClusterConfig[String, String](config))
      .use { valkey =>
        for {
          _ <- IO.println("=== Cluster Operations ===")

          // Multi-key operations work seamlessly across slots!
          _ <- valkey.mSet(
            Map(
              "key1" -> "value1",
              "key2" -> "value2",
              "key3" -> "value3"
            )
          )

          values <- valkey.mGet(Set("key1", "key2", "key3"))
          _ <- IO.println(
            s"Retrieved from cluster: ${values.getOrElse(Map.empty)}"
          )

          _ <- IO.println("\n=== Cluster example completed! ===")
        } yield ()
      }
      .handleErrorWith { err =>
        IO.println(s"Note: This example requires a running Valkey cluster") *>
          IO.println(s"Error: ${err.getMessage}")
      }
  }
}
