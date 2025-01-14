package dev.profunktor.valkey4cats

import cats.effect.{IO, Resource}
import dev.profunktor.valkey4cats.effect.Log
import munit.CatsEffectSuite

abstract class ClusterTestSuite extends CatsEffectSuite {

  implicit val logger: Log[IO] = Log.Stdout.instance[IO]

  private val clusterUri = "valkey://127.0.0.1:30001"

  override def munitIgnore: Boolean = !isClusterReachable

  private def isClusterReachable: Boolean =
    try {
      val socket = new java.net.Socket()
      socket.connect(new java.net.InetSocketAddress("127.0.0.1", 30001), 200)
      socket.close()
      true
    } catch {
      case _: Exception => false
    }

  def clusterClient: Resource[IO, ValkeyCommands[IO, String, String]] =
    Valkey[IO].clusterUtf8(clusterUri)
}
