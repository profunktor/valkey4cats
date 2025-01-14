package dev.profunktor.valkey4cats.syntax

import dev.profunktor.valkey4cats.model.ValkeyUri
import org.typelevel.literally.Literally

import scala.quoted.*

object macros {

  object ValkeyLiteral extends Literally[ValkeyUri] {

    override def validate(
        s: String
    )(using Quotes): Either[String, Expr[ValkeyUri]] = {
      ValkeyUri.fromString(s) match {
        case Left(e) => Left(e.getMessage)
        case Right(_) =>
          val uriStr = Expr(s)
          Right('{ ValkeyUri.unsafeFromString($uriStr) })
      }
    }

  }

}
