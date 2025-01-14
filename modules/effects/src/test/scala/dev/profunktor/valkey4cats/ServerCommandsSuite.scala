package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.arguments.InfoSection
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

class ServerCommandsSuite extends ValkeyTestSuite {

  test("INFO should return server information") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.info
      } yield {
        val Ok(info) = result: @unchecked
        assert(
          info.contains("redis_version") || info.contains("valkey_version")
        )
      }
    }
  }

  test("INFO with section should return specific information") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.info(Set(InfoSection.Server))
      } yield {
        val Ok(info) = result: @unchecked
        assert(info.contains("tcp_port"))
      }
    }
  }

  test("TIME should return server time") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.time
      } yield {
        val Ok(t) = result: @unchecked
        assert(t.unixSeconds > 0)
        assert(t.microseconds >= 0)
      }
    }
  }

  test("DBSIZE should return number of keys") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.set("dbsize-test", "v")
        result <- valkey.dbSize
      } yield {
        val Ok(size) = result: @unchecked
        assert(size >= 1)
      }
    }
  }

  test("LASTSAVE should return a timestamp") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.lastSave
      } yield {
        val Ok(ts) = result: @unchecked
        assert(ts >= 0)
      }
    }
  }

  test("CONFIG GET should return parameters") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.configGet(Set("maxmemory"))
      } yield {
        val Ok(config) = result: @unchecked
        assert(config.contains("maxmemory"))
      }
    }
  }

  test("LOLWUT should return ASCII art") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.lolwut
      } yield {
        val Ok(art) = result: @unchecked
        assert(art.nonEmpty)
      }
    }
  }
}
