package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.arguments.FlushMode
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

class ScriptingCommandsSuite extends ValkeyTestSuite {

  test("SCRIPT FLUSH should succeed") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.scriptFlush
      } yield assertEquals(result, Ok(()))
    }
  }

  test("SCRIPT FLUSH with SYNC mode should succeed") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.scriptFlush(FlushMode.Sync)
      } yield assertEquals(result, Ok(()))
    }
  }

  test("SCRIPT FLUSH with ASYNC mode should succeed") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.scriptFlush(FlushMode.Async)
      } yield assertEquals(result, Ok(()))
    }
  }

  test("SCRIPT EXISTS should return false for non-existent scripts") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.scriptExists(
          "0000000000000000000000000000000000000000"
        )
      } yield {
        val Ok(exists) = result: @unchecked
        assertEquals(exists, List(false))
      }
    }
  }

  test("SCRIPT EXISTS with multiple sha1s should return list of booleans") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.scriptExists(
          "0000000000000000000000000000000000000000",
          "1111111111111111111111111111111111111111"
        )
      } yield {
        val Ok(exists) = result: @unchecked
        assertEquals(exists, List(false, false))
      }
    }
  }

  test("SCRIPT KILL should return error when no script is running") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.scriptKill
      } yield assert(result.isErr)
    }
  }

  test("FCALL with non-existent function should return error") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.fcall("nonexistent_func", List.empty, List.empty)
      } yield assert(result.isErr)
    }
  }
}
