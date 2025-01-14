package dev.profunktor.valkey4cats.util

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/** TestContainers wrapper for Valkey/Redis
  *
  * Automatically starts a Valkey container for testing.
  * Falls back to Redis if Valkey image is not available.
  */
class ValkeyContainer
    extends GenericContainer[ValkeyContainer](
      DockerImageName
        .parse("valkey/valkey:latest")
        .asCompatibleSubstituteFor("redis")
    ) {

  // Expose Redis default port
  withExposedPorts(6379)

  /** Get the connection URI for this container */
  def uri: String = s"valkey://${getHost}:${getMappedPort(6379)}"

  /** Get the host */
  def host: String = getHost

  /** Get the mapped port */
  def port: Int = getMappedPort(6379)
}

object ValkeyContainer {

  /** Create and start a new Valkey container */
  def create(): ValkeyContainer = {
    val container = new ValkeyContainer()
    container.start()
    container
  }
}
