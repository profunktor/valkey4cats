package dev.profunktor.valkey4cats.effect

import cats.ApplicativeThrow
import cats.effect.kernel.Async
import cats.effect.kernel.syntax.monadCancel._
import cats.syntax.all._

import java.util.concurrent._

/** Capability to lift Java CompletableFuture into effect F */
private[valkey4cats] trait FutureLift[F[_]] {
  def delay[A](thunk: => A): F[A]
  def blocking[A](thunk: => A): F[A]
  def guarantee[A](fa: F[A], fu: F[Unit]): F[A]
  def lift[A](fa: => CompletableFuture[A]): F[A]
}

object FutureLift {

  def apply[F[_]: FutureLift]: FutureLift[F] = implicitly

  implicit def forAsync[F[_]: Async]: FutureLift[F] =
    new FutureLift[F] {
      val F = Async[F]

      def delay[A](thunk: => A): F[A] = F.delay(thunk)

      def blocking[A](thunk: => A): F[A] = F.blocking(thunk)

      def guarantee[A](fa: F[A], fu: F[Unit]): F[A] = fa.guarantee(fu)

      def lift[A](fa: => CompletableFuture[A]): F[A] =
        F.fromCompletableFuture(F.delay(fa))
    }

  implicit final class FutureLiftOps[
      F[_]: ApplicativeThrow: FutureLift: Log,
      A
  ](fa: => CompletableFuture[A]) {
    def futureLift: F[A] =
      FutureLift[F].lift(fa).onError { case e: ExecutionException =>
        Log[F].error(s"${e.getMessage()} - ${Option(e.getCause())}")
      }
  }
}
