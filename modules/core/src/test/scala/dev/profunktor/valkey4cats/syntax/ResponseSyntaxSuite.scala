package dev.profunktor.valkey4cats.syntax

import cats.effect.IO
import dev.profunktor.valkey4cats.model.{ValkeyError, ValkeyResponse}
import dev.profunktor.valkey4cats.syntax.response.*
import munit.CatsEffectSuite

class ResponseSyntaxSuite extends CatsEffectSuite {

  test(".direct should unwrap Ok value") {
    val fa: IO[ValkeyResponse[String]] = IO.pure(ValkeyResponse.Ok("hello"))
    fa.direct.map(value => assertEquals(value, "hello"))
  }

  test(".direct should raise ValkeyDomainError for Err") {
    val err = ValkeyError.WrongType("WRONGTYPE wrong type")
    val fa: IO[ValkeyResponse[String]] = IO.pure(ValkeyResponse.Err(err))

    fa.direct.attempt.map {
      case Left(ValkeyResponse.ValkeyDomainError(ValkeyError.WrongType(msg))) =>
        assert(msg.contains("WRONGTYPE"))
      case Left(e) =>
        fail(s"Expected ValkeyDomainError(WrongType), got $e")
      case Right(_) =>
        fail("Expected error, got success")
    }
  }

  test(".direct should propagate infrastructure errors") {
    val fa: IO[ValkeyResponse[String]] =
      IO.raiseError(new RuntimeException("connection lost"))

    fa.direct.attempt.map {
      case Left(e: RuntimeException) =>
        assertEquals(e.getMessage, "connection lost")
      case other =>
        fail(s"Expected RuntimeException, got $other")
    }
  }

  test(".direct should work with Option values") {
    val fa: IO[ValkeyResponse[Option[String]]] =
      IO.pure(ValkeyResponse.Ok(None))

    fa.direct.map(value => assertEquals(value, None))
  }

  test(".direct should work with numeric values") {
    val fa: IO[ValkeyResponse[Long]] = IO.pure(ValkeyResponse.Ok(42L))
    fa.direct.map(value => assertEquals(value, 42L))
  }

  test(".direct should compose with flatMap") {
    val get1: IO[ValkeyResponse[Option[String]]] =
      IO.pure(ValkeyResponse.Ok(Some("10")))
    val get2: IO[ValkeyResponse[Option[String]]] =
      IO.pure(ValkeyResponse.Ok(Some("20")))

    for {
      v1 <- get1.direct
      v2 <- get2.direct
    } yield {
      assertEquals(
        v1.map(_.toInt).getOrElse(0) + v2.map(_.toInt).getOrElse(0),
        30
      )
    }
  }
}
