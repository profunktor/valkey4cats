package dev.profunktor.valkey4cats

import scala.compiletime.uninitialized
import cats.effect.{IO, Resource}
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.util.ValkeyContainer
import munit.CatsEffectSuite

/** Base suite for integration tests that need a running Valkey instance
  *
  * Automatically manages a TestContainers Valkey instance for each test suite.
  */
abstract class ValkeyTestSuite extends CatsEffectSuite {

  // Implicit logger for tests
  implicit val logger: Log[IO] = Log.Stdout.instance[IO]

  // Shared Valkey container for all tests in this suite
  private var container: ValkeyContainer = uninitialized

  override def beforeAll(): Unit = {
    super.beforeAll()
    container = ValkeyContainer.create()
  }

  override def afterAll(): Unit = {
    if (container != null) {
      container.stop()
    }
    super.afterAll()
  }

  /** Get the URI for the running Valkey container */
  def valkeyUri: String = container.uri

  /** Get the host for the running Valkey container */
  def valkeyHost: String = container.host

  /** Get the port for the running Valkey container */
  def valkeyPort: Int = container.port

  /** Create a Valkey client connected to the test container */
  def valkeyClient: Resource[IO, ValkeyCommands[IO, String, String]] =
    Valkey[IO].utf8(valkeyUri)
}
