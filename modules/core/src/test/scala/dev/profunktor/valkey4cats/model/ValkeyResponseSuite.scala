package dev.profunktor.valkey4cats.model

import cats.effect.IO
import munit.CatsEffectSuite

class ValkeyResponseSuite extends CatsEffectSuite {

  // ==================== ValkeyDomainError extractor ====================

  test("ValkeyDomainError.unapply should extract typed error") {
    val error = ValkeyError.WrongType("WRONGTYPE Operation against a key")
    val domainError = new ValkeyResponse.ValkeyDomainError(error)

    domainError match {
      case ValkeyResponse.ValkeyDomainError(ValkeyError.WrongType(msg)) =>
        assertEquals(msg, "WRONGTYPE Operation against a key")
      case _ => fail("unapply did not extract WrongType")
    }
  }

  test("ValkeyDomainError.unapply should extract CrossSlot error") {
    val error = ValkeyError.CrossSlot(
      "CROSSSLOT Keys in request don't hash to the same slot"
    )
    val domainError = new ValkeyResponse.ValkeyDomainError(error)

    domainError match {
      case ValkeyResponse.ValkeyDomainError(ValkeyError.CrossSlot(msg)) =>
        assert(msg.contains("CROSSSLOT"))
      case _ => fail("unapply did not extract CrossSlot")
    }
  }

  test("ValkeyDomainError.unapply should extract AuthError") {
    val error = ValkeyError.AuthError("NOAUTH Authentication required")
    val domainError = new ValkeyResponse.ValkeyDomainError(error)

    domainError match {
      case ValkeyResponse.ValkeyDomainError(ValkeyError.AuthError(_)) => ()
      case _ => fail("unapply did not extract AuthError")
    }
  }

  test("ValkeyDomainError.unapply should return None for other exceptions") {
    val ex = new RuntimeException("not a valkey error")

    ex match {
      case ValkeyResponse.ValkeyDomainError(_) =>
        fail("unapply should not match non-ValkeyDomainError")
      case _ => ()
    }
  }

  test("ValkeyDomainError.unapply should work in handleErrorWith") {
    val error = ValkeyError.ReadOnly(
      "READONLY You can't write against a read only replica"
    )
    val raised: IO[String] =
      IO.raiseError(new ValkeyResponse.ValkeyDomainError(error))

    raised
      .handleErrorWith {
        case ValkeyResponse.ValkeyDomainError(ValkeyError.ReadOnly(msg)) =>
          IO.pure(s"recovered: $msg")
        case _ => IO.pure("unexpected")
      }
      .flatMap { result =>
        IO(assert(result.startsWith("recovered: READONLY")))
      }
  }

  // ==================== liftTo round-trip with extractor ====================

  test("liftTo should raise ValkeyDomainError extractable by unapply") {
    val resp: ValkeyResponse[String] =
      ValkeyResponse.Err(ValkeyError.Busy("BUSY Redis is busy"))

    resp.liftTo[IO, String].attempt.flatMap {
      case Left(ValkeyResponse.ValkeyDomainError(ValkeyError.Busy(msg))) =>
        IO(assert(msg.contains("BUSY")))
      case Left(e) =>
        IO(fail(s"Expected ValkeyDomainError(Busy), got $e"))
      case Right(_) =>
        IO(fail("Expected error, got success"))
    }
  }

  test("liftTo should succeed for Ok values") {
    val resp: ValkeyResponse[Int] = ValkeyResponse.Ok(42)

    resp.liftTo[IO, Int].map { value =>
      assertEquals(value, 42)
    }
  }

  // ==================== ValkeyResponse combinators ====================

  test("map should transform Ok values") {
    val resp = ValkeyResponse.Ok(10)
    assertEquals(resp.map(_ * 2), ValkeyResponse.Ok(20))
  }

  test("map should not affect Err") {
    val err = ValkeyResponse.Err(ValkeyError.CommandError("ERR some error"))
    assertEquals(err.map((_: Nothing) => 42), err)
  }

  test("flatMap should chain Ok values") {
    val resp = ValkeyResponse.Ok(10)
    assertEquals(
      resp.flatMap(x => ValkeyResponse.Ok(x + 5)),
      ValkeyResponse.Ok(15)
    )
  }

  test("flatMap should short-circuit on Err") {
    val err: ValkeyResponse[Int] =
      ValkeyResponse.Err(ValkeyError.CommandError("ERR"))
    assertEquals(
      err.flatMap(x => ValkeyResponse.Ok(x + 5)),
      err
    )
  }

  test("fold should extract from Ok") {
    val resp = ValkeyResponse.Ok("hello")
    assertEquals(resp.fold(_ => "error", identity), "hello")
  }

  test("fold should extract from Err") {
    val resp = ValkeyResponse.Err(ValkeyError.CommandError("ERR oops"))
    assertEquals(resp.fold(_.message, (_: Nothing) => ""), "ERR oops")
  }

  test("toEither should convert Ok to Right") {
    assertEquals(ValkeyResponse.Ok(42).toEither, Right(42))
  }

  test("toEither should convert Err to Left") {
    val err = ValkeyError.CommandError("ERR")
    assertEquals(ValkeyResponse.Err(err).toEither, Left(err))
  }

  test("toOption should convert Ok to Some") {
    assertEquals(ValkeyResponse.Ok(42).toOption, Some(42))
  }

  test("toOption should convert Err to None") {
    assertEquals(
      ValkeyResponse.Err(ValkeyError.CommandError("ERR")).toOption,
      None
    )
  }

  test("getOrElse should return value for Ok") {
    assertEquals(ValkeyResponse.Ok(42).getOrElse(0), 42)
  }

  test("getOrElse should return default for Err") {
    assertEquals(
      ValkeyResponse.Err(ValkeyError.CommandError("ERR")).getOrElse(0),
      0
    )
  }

  test("orElse should return original for Ok") {
    val ok = ValkeyResponse.Ok(42)
    assertEquals(ok.orElse(ValkeyResponse.Ok(0)), ok)
  }

  test("orElse should return alternative for Err") {
    val err: ValkeyResponse[Int] =
      ValkeyResponse.Err(ValkeyError.CommandError("ERR"))
    val alt = ValkeyResponse.Ok(0)
    assertEquals(err.orElse(alt), alt)
  }

  test("isOk / isErr should be consistent") {
    val ok = ValkeyResponse.Ok(42)
    val err = ValkeyResponse.Err(ValkeyError.CommandError("ERR"))
    assert(ok.isOk)
    assert(!ok.isErr)
    assert(err.isErr)
    assert(!err.isOk)
  }
}
