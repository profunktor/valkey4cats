package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

class ConnectionCommandsSuite extends ValkeyTestSuite {

  test("PING should return PONG") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.ping
      } yield {
        val Ok(pong) = result: @unchecked
        assertEquals(pong, "PONG")
      }
    }
  }

  test("PING with message should echo the message back") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.ping("hello")
      } yield {
        val Ok(msg) = result: @unchecked
        assertEquals(msg, "hello")
      }
    }
  }

  test("ECHO should return the message") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.echo("test-message")
      } yield {
        val Ok(msg) = result: @unchecked
        assertEquals(msg, "test-message")
      }
    }
  }

  test("CLIENT ID should return a positive long") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.clientId
      } yield {
        val Ok(id) = result: @unchecked
        assert(id > 0)
      }
    }
  }

  test("CLIENT GETNAME should return None when no name is set") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.clientGetName
      } yield {
        val Ok(name) = result: @unchecked
        assert(name.isEmpty)
      }
    }
  }

  test("SELECT should switch database without error") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.select(0)
      } yield {
        val Ok(_) = result: @unchecked
      }
    }
  }
}
