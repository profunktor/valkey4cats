package dev.profunktor.valkey4cats.syntax

import cats.MonadThrow
import dev.profunktor.valkey4cats.model.ValkeyResponse

object response {
  implicit class ValkeyResponseOps[F[_], A](
      private val fa: F[ValkeyResponse[A]]
  ) extends AnyVal {

    /** Unwrap the response: returns F[A] for Ok, raises [[ValkeyResponse.ValkeyDomainError]] for Err. */
    def direct(implicit F: MonadThrow[F]): F[A] =
      F.flatMap(fa)(_.liftTo[F, A])
  }
}
