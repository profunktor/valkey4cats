package dev.profunktor.valkey4cats.tx

import cats.effect.kernel.{Async, Ref}
import cats.syntax.functor._

/** Provides a way to store transactional results for later retrieval.
 */
trait TxStore[F[_], K, V] {
  def get: F[Map[K, V]]
  def set(key: K)(v: V): F[Unit]
}

object TxStore {
  private[valkey4cats] def make[F[_]: Async, K, V]: F[TxStore[F, K, V]] =
    Ref.of[F, Map[K, V]](Map.empty).map { ref =>
      new TxStore[F, K, V] {
        def get: F[Map[K, V]] = ref.get
        def set(key: K)(v: V): F[Unit] = ref.update(_.updated(key, v))
      }
    }
}
