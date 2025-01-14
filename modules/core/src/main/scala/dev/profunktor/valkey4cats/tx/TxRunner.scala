package dev.profunktor.valkey4cats.tx

import cats.effect.kernel.*
import cats.effect.kernel.syntax.all.*
import cats.syntax.all.*
import dev.profunktor.valkey4cats.effect.TxExecutor

private[valkey4cats] trait TxRunner[F[_]] {
  def run[A](
      acquire: F[Unit],
      release: F[Unit],
      onError: F[Unit]
  )(
      fs: TxStore[F, String, A] => List[F[Unit]]
  ): F[Map[String, A]]
  def liftK[G[_]: Async]: TxRunner[G]
}

private[valkey4cats] object TxRunner {
  private[valkey4cats] def make[F[_]: Async](t: TxExecutor[F]): TxRunner[F] =
    new TxRunner[F] {
      def run[A](
          acquire: F[Unit],
          release: F[Unit],
          onError: F[Unit]
      )(
          fs: TxStore[F, String, A] => List[F[Unit]]
      ): F[Map[String, A]] =
        TxStore.make[F, String, A].flatMap { store =>
          (
            Deferred[F, Unit],
            Ref.of[F, List[Fiber[F, Throwable, Unit]]](List.empty)
          ).tupled.flatMap { case (gate, fbs) =>
            t.eval(acquire)
              .bracketCase { _ =>
                fs(store)
                  .traverse_(f => t.start(f).flatMap(fb => fbs.update(_ :+ fb)))
                  .guarantee(gate.complete(()).void)
              } {
                case (_, Outcome.Succeeded(_)) =>
                  gate.get *> t
                    .eval(release)
                    .guarantee(fbs.get.flatMap(_.traverse_(_.join)))
                case (_, _) =>
                  t.eval(onError)
                    .guarantee(fbs.get.flatMap(_.traverse_(_.cancel)))
              }
          } *> store.get
        }

      def liftK[G[_]: Async]: TxRunner[G] = make[G](t.liftK[G])
    }
}
