package dev.profunktor.valkey4cats

import cats.effect.IO
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok
import munit.CatsEffectSuite

class NativeClientSmokeTest extends CatsEffectSuite {

  implicit val logger: Log[IO] = Log.Stdout.instance[IO]

  private val uri = sys.env.getOrElse("VALKEY_TEST_URI", "valkey://127.0.0.1:6380")

  test("PING") {
    Valkey[IO].utf8(uri).use(_.ping).map(r => assertEquals(r, Ok("PONG")))
  }

  test("SET and GET") {
    Valkey[IO].utf8(uri).use { cmd =>
      for
        _ <- cmd.set("smoke-key", "smoke-value")
        r <- cmd.get("smoke-key")
        _ <- cmd.del("smoke-key")
      yield assertEquals(r, Ok(Some("smoke-value")))
    }
  }

  test("DEL returns count") {
    Valkey[IO].utf8(uri).use { cmd =>
      for
        _ <- cmd.set("del-key-1", "v1")
        _ <- cmd.set("del-key-2", "v2")
        r <- cmd.del("del-key-1", "del-key-2")
      yield assertEquals(r, Ok(2L))
    }
  }
}
