package dev.profunktor.valkey4cats.model

import com.comcast.ip4s.{host, port}
import munit.FunSuite

class ValkeyUriSuite extends FunSuite {

  private def rightOrFail[A](either: Either[?, A]): A =
    either.fold(e => fail(s"Expected Right but got Left($e)"), identity)

  test("fromString should parse simple valkey URI") {
    val uri = rightOrFail(ValkeyUri.fromString("valkey://localhost:6379"))

    assertEquals(uri.scheme, ValkeyUri.Scheme.Valkey)
    assertEquals(uri.host, host"localhost")
    assertEquals(uri.port, port"6379")
    assertEquals(uri.useTls, false)
    assertEquals(uri.credentials, None)
    assertEquals(uri.database, None)
  }

  test("fromString should parse valkeys URI with TLS") {
    val uri = rightOrFail(ValkeyUri.fromString("valkeys://secure-server:6380"))

    assertEquals(uri.scheme, ValkeyUri.Scheme.Valkeys)
    assertEquals(uri.host, host"secure-server")
    assertEquals(uri.port, port"6380")
    assertEquals(uri.useTls, true)
  }

  test("fromString should parse redis URI for compatibility") {
    val uri = rightOrFail(ValkeyUri.fromString("redis://localhost:6379"))

    assertEquals(uri.scheme, ValkeyUri.Scheme.Redis)
    assertEquals(uri.useTls, false)
  }

  test("fromString should parse rediss URI with TLS") {
    val uri = rightOrFail(ValkeyUri.fromString("rediss://secure-server:6380"))

    assertEquals(uri.scheme, ValkeyUri.Scheme.Rediss)
    assertEquals(uri.useTls, true)
  }

  test("fromString should parse URI with password") {
    val uri = rightOrFail(
      ValkeyUri.fromString("valkey://:mypassword@localhost:6379")
    )

    assert(uri.credentials.isDefined)
    uri.credentials match {
      case Some(ServerCredentials.Password(pwd)) =>
        assertEquals(pwd, "mypassword")
      case _ => fail("Expected Password credentials")
    }
  }

  test("fromString should parse URI with username and password") {
    val uri = rightOrFail(
      ValkeyUri.fromString("valkey://alice:secret@localhost:6379")
    )

    assert(uri.credentials.isDefined)
    uri.credentials match {
      case Some(ServerCredentials.UsernamePassword(user, pwd)) =>
        assertEquals(user, "alice")
        assertEquals(pwd, "secret")
      case _ => fail("Expected UsernamePassword credentials")
    }
  }

  test("fromString should parse URI with database number") {
    val uri = rightOrFail(ValkeyUri.fromString("valkey://localhost:6379/2"))

    assertEquals(uri.database.map(_.value), Some(2))
  }

  test("fromString should use default port when not specified") {
    val uri = rightOrFail(ValkeyUri.fromString("valkey://localhost"))

    assertEquals(uri.port, port"6379")
  }

  test("fromString should reject invalid scheme") {
    val result = ValkeyUri.fromString("http://localhost:6379")

    assert(result.isLeft)
    result.left.foreach(e => assert(e.getMessage.contains("Invalid scheme")))
  }

  test("toURI should round-trip simple URI") {
    val original = "valkey://localhost:6379"
    val uri = rightOrFail(ValkeyUri.fromString(original))

    assertEquals(uri.toURI.toString, original)
  }

  test("toURI should round-trip URI with credentials") {
    val original = "valkey://alice:secret@localhost:6379"
    val uri = rightOrFail(ValkeyUri.fromString(original))

    assertEquals(uri.toURI.toString, original)
  }

  test("toURI should round-trip URI with database") {
    val original = "valkey://localhost:6379/2"
    val uri = rightOrFail(ValkeyUri.fromString(original))

    assertEquals(uri.toURI.toString, original)
  }

  test("ValkeyClientConfig.fromUri should create config from ValkeyUri") {
    val uri = rightOrFail(
      ValkeyUri.fromString("valkeys://alice:secret@secure-host:6380/3")
    )
    val config = ValkeyClientConfig.fromUri(uri)

    assertEquals(config.addresses.size, 1)
    assertEquals(config.addresses.head.host, host"secure-host")
    assertEquals(config.addresses.head.port, port"6380")
    assertEquals(config.tlsMode.isEnabled, true)
    assert(config.credentials.isDefined)
    assertEquals(config.databaseId.map(_.value), Some(3))
  }

  test("ValkeyClientConfig.fromUriString should delegate to ValkeyUri") {
    val config = rightOrFail(
      ValkeyClientConfig.fromUriString("valkey://localhost:6379/1")
    )

    assertEquals(config.addresses.head.host, host"localhost")
    assertEquals(config.addresses.head.port, port"6379")
    assertEquals(config.databaseId.map(_.value), Some(1))
  }
}
