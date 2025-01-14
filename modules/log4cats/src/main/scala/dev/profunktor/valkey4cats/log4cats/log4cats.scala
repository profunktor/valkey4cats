package dev.profunktor.valkey4cats.log4cats

import dev.profunktor.valkey4cats.effect.Log
import org.typelevel.log4cats.Logger

/** Provides a `Log[F]` instance backed by log4cats `Logger[F]` for use with valkey4cats logging */
object log4cats {

  implicit def log4CatsInstance[F[_]: Logger]: Log[F] =
    new Log[F] {
      def debug(msg: => String): F[Unit] = Logger[F].debug(msg)
      def error(msg: => String): F[Unit] = Logger[F].error(msg)
      def info(msg: => String): F[Unit] = Logger[F].info(msg)
    }

}
