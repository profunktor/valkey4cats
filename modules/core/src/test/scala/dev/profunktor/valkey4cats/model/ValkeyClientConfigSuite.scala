package dev.profunktor.valkey4cats.model

import com.comcast.ip4s.{host, port}
import munit.FunSuite

import scala.concurrent.duration.*

class ValkeyClientConfigSuite extends FunSuite {

  test("fromUriString should parse simple redis URI") {
    val result = ValkeyClientConfig.fromUriString("redis://localhost:6379")

    assert(result.isRight)
    val config = result.toOption.get

    assertEquals(config.addresses.size, 1)
    assertEquals(config.addresses.head.host, host"localhost")
    assertEquals(config.addresses.head.port, port"6379")
    assertEquals(config.tlsMode.isEnabled, false)
    assertEquals(config.credentials, None)
    assertEquals(config.databaseId, None)
  }

  test("fromUriString should parse simple valkey URI") {
    val result = ValkeyClientConfig.fromUriString("valkey://localhost:6379")

    assert(result.isRight)
    val config = result.toOption.get

    assertEquals(config.addresses.size, 1)
    assertEquals(config.addresses.head.host, host"localhost")
    assertEquals(config.addresses.head.port, port"6379")
    assertEquals(config.tlsMode.isEnabled, false)
    assertEquals(config.credentials, None)
    assertEquals(config.databaseId, None)
  }

  test("fromUriString should parse rediss URI with TLS") {
    val result = ValkeyClientConfig.fromUriString("rediss://secure-server:6380")

    assert(result.isRight)
    val config = result.toOption.get

    assertEquals(config.addresses.head.host, host"secure-server")
    assertEquals(config.addresses.head.port, port"6380")
    assertEquals(config.tlsMode.isEnabled, true)
  }

  test("fromUriString should parse valkeys URI with TLS") {
    val result =
      ValkeyClientConfig.fromUriString("valkeys://secure-server:6380")

    assert(result.isRight)
    val config = result.toOption.get

    assertEquals(config.addresses.head.host, host"secure-server")
    assertEquals(config.addresses.head.port, port"6380")
    assertEquals(config.tlsMode.isEnabled, true)
  }

  test("fromUriString should parse URI with password") {
    val result =
      ValkeyClientConfig.fromUriString("redis://:mypassword@localhost:6379")

    assert(result.isRight)
    val config = result.toOption.get

    assert(config.credentials.isDefined)
    config.credentials.get match {
      case ServerCredentials.Password(pwd) => assertEquals(pwd, "mypassword")
      case _ => fail("Expected Password credentials")
    }
  }

  test("fromUriString should parse valkey URI with password") {
    val result =
      ValkeyClientConfig.fromUriString("valkey://:mypassword@localhost:6379")

    assert(result.isRight)
    val config = result.toOption.get

    assert(config.credentials.isDefined)
    config.credentials.get match {
      case ServerCredentials.Password(pwd) => assertEquals(pwd, "mypassword")
      case _ => fail("Expected Password credentials")
    }
  }

  test("fromUriString should parse URI with username and password") {
    val result =
      ValkeyClientConfig.fromUriString("redis://alice:secret@localhost:6379")

    assert(result.isRight)
    val config = result.toOption.get

    assert(config.credentials.isDefined)
    config.credentials.get match {
      case ServerCredentials.UsernamePassword(user, pwd) =>
        assertEquals(user, "alice")
        assertEquals(pwd, "secret")
      case _ => fail("Expected UsernamePassword credentials")
    }
  }

  test("fromUriString should parse URI with database number") {
    val result = ValkeyClientConfig.fromUriString("redis://localhost:6379/2")

    assert(result.isRight)
    val config = result.toOption.get

    assertEquals(config.databaseId.map(_.value), Some(2))
  }

  test("fromUriString should parse valkey URI with database number") {
    val result = ValkeyClientConfig.fromUriString("valkey://localhost:6379/3")

    assert(result.isRight)
    val config = result.toOption.get

    assertEquals(config.databaseId.map(_.value), Some(3))
  }

  test("fromUriString should use default port when not specified") {
    val result = ValkeyClientConfig.fromUriString("redis://localhost")

    assert(result.isRight)
    val config = result.toOption.get

    assertEquals(config.addresses.head.port, port"6379")
  }

  test("fromUriString should reject invalid scheme") {
    val result = ValkeyClientConfig.fromUriString("http://localhost:6379")

    assert(result.isLeft)
  }

  test("builder should allow fluent configuration") {
    val config = ValkeyClientConfig.builder
      .addAddress(host"myhost", port"7000")
      .withTlsEnabled
      .withPassword("secret")
      .withDatabase(DatabaseId.unsafe(3))
      .withClientName("my-app")

    assertEquals(config.addresses.size, 2) // localhost + myhost
    assert(config.addresses.exists(_.host == host"myhost"))
    assertEquals(config.tlsMode.isEnabled, true)
    assert(config.credentials.isDefined)
    assertEquals(config.databaseId.map(_.value), Some(3))
    assertEquals(config.clientName, Some("my-app"))
  }

  test("localhost config should have sensible defaults") {
    val config = ValkeyClientConfig.localhost

    assertEquals(config.addresses.size, 1)
    assertEquals(config.addresses.head.host, host"localhost")
    assertEquals(config.addresses.head.port, port"6379")
    assertEquals(config.tlsMode.isEnabled, false)
  }

  test("apply should reject empty addresses") {
    val result = ValkeyClientConfig(addresses = List.empty)
    assert(result.isLeft)
    assert(result.left.exists(_.contains("At least one address is required")))
  }

  test("withDatabase should reject invalid database IDs") {
    val result = ValkeyClientConfig.localhost.withDatabase(999)
    assert(result.isLeft)
  }

  test("withDatabase should accept valid database IDs") {
    val result = ValkeyClientConfig.localhost.withDatabase(15)
    assert(result.isRight)
    assertEquals(result.toOption.get.databaseId.map(_.value), Some(15))
  }

  test("ServerCredentials equality should work correctly") {
    val pwd1 = ServerCredentials.password("foo")
    val pwd2 = ServerCredentials.password("foo")
    val pwd3 = ServerCredentials.password("bar")

    assertEquals(pwd1, pwd2)
    assertNotEquals(pwd1, pwd3)
  }

  test("fromUriString should reject invalid database path") {
    val result =
      ValkeyClientConfig.fromUriString("redis://localhost:6379/notanumber")
    assert(result.isLeft)
  }

  test("fromUriString should reject database ID out of range") {
    val result =
      ValkeyClientConfig.fromUriString("redis://localhost:6379/999")
    assert(result.isLeft)
  }

  test("NodeAddress.fromString should validate host and port") {
    assert(NodeAddress.fromString("localhost", 6379).isRight)
    assert(NodeAddress.fromString("localhost", -1).isLeft)
    assert(NodeAddress.fromString("localhost", 70000).isLeft)
  }

  test("withReconnectStrategy should be available on standalone config") {
    val strategy = BackOffStrategy.FixedDelay(3, 100.millis)
    val config = ValkeyClientConfig.localhost.withReconnectStrategy(strategy)
    assertEquals(config.reconnectStrategy, Some(strategy))
  }

  test("shared builder methods should preserve standalone-specific fields") {
    val config = ValkeyClientConfig.localhost
      .withDatabase(DatabaseId.unsafe(5))
      .withTlsEnabled
      .withClientName("test")

    assertEquals(config.databaseId.map(_.value), Some(5))
    assertEquals(config.tlsMode.isEnabled, true)
    assertEquals(config.clientName, Some("test"))
  }

  test("cluster config builder should allow fluent configuration") {
    val config = ValkeyClusterConfig
      .builder(host"node1", port"7000")
      .addAddress(host"node2", port"7001")
      .withTlsEnabled
      .withPassword("secret")
      .withRefreshTopologyFromInitialNodesEnabled

    assertEquals(config.addresses.size, 2)
    assertEquals(config.tlsMode.isEnabled, true)
    assert(config.credentials.isDefined)
    assertEquals(config.refreshTopologyFromInitialNodes, Some(true))
  }

  test("cluster config should reject empty addresses") {
    val result = ValkeyClusterConfig(addresses = List.empty)
    assert(result.isLeft)
    assert(
      result.left.exists(_.contains("At least one address is required"))
    )
  }
}
