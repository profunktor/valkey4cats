package dev.profunktor.valkey4cats

import scala.compiletime.uninitialized
import cats.effect.{IO, Resource}
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.util.ValkeyContainer
import munit.CatsEffectSuite

/** Base suite for integration tests that need a running Valkey instance
  *
  * Uses VALKEY_TEST_URI env var if set, otherwise starts a TestContainers Valkey instance.
  */
abstract class ValkeyTestSuite extends CatsEffectSuite {

  implicit val logger: Log[IO] = Log.Stdout.instance[IO]

  private val fixedUri: Option[String] = sys.env.get("VALKEY_TEST_URI")
  private var container: ValkeyContainer = uninitialized

  override def beforeAll(): Unit = {
    super.beforeAll()
    if (fixedUri.isEmpty) {
      container = ValkeyContainer.create()
    }
  }

  override def afterAll(): Unit = {
    if (container != null) {
      container.stop()
    }
    super.afterAll()
  }

  def valkeyUri: String = fixedUri.getOrElse(container.uri)

  def valkeyHost: String = fixedUri.map(_ => "127.0.0.1").getOrElse(container.host)

  def valkeyPort: Int = fixedUri.map(_.split(":").last.toInt).getOrElse(container.port)

  def valkeyClient: Resource[IO, ValkeyCommands[IO, String, String]] =
    Valkey[IO].utf8(valkeyUri)
}
