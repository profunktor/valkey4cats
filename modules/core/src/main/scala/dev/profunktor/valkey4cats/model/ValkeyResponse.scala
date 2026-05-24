package dev.profunktor.valkey4cats.model

import cats.{
  Applicative,
  ApplicativeThrow,
  Eval,
  Functor,
  Monad,
  MonadError,
  Traverse
}

/** Core result wrapper for all Valkey commands, representing either a successful value or a domain error. */
sealed trait ValkeyResponse[+A] {

  /** Catamorphism: extract a value by handling both error and success cases. */
  def fold[B](onErr: ValkeyError => B, onOk: A => B): B = this match {
    case ValkeyResponse.Ok(value)  => onOk(value)
    case ValkeyResponse.Err(error) => onErr(error)
  }

  /** Transform the success value, preserving errors unchanged. */
  def map[B](f: A => B): ValkeyResponse[B] = this match {
    case ValkeyResponse.Ok(value)  => ValkeyResponse.Ok(f(value))
    case e @ ValkeyResponse.Err(_) => e
  }

  /** Sequence a dependent computation on the success value, short-circuiting on error. */
  def flatMap[B](f: A => ValkeyResponse[B]): ValkeyResponse[B] = this match {
    case ValkeyResponse.Ok(value)  => f(value)
    case e @ ValkeyResponse.Err(_) => e
  }

  /** Convert to a standard Either with ValkeyError on the left. */
  def toEither: Either[ValkeyError, A] = fold(Left(_), Right(_))

  /** Discard the error and return the success value as an Option. */
  def toOption: Option[A] = fold(_ => None, Some(_))

  def getOrElse[B >: A](default: => B): B = fold(_ => default, identity)

  def orElse[B >: A](alt: => ValkeyResponse[B]): ValkeyResponse[B] =
    this match {
      case ok @ ValkeyResponse.Ok(_) => ok
      case ValkeyResponse.Err(_)     => alt
    }

  def isOk: Boolean = fold(_ => false, _ => true)

  def isErr: Boolean = !isOk

  /** Lift into an effect F, raising [[ValkeyResponse.ValkeyDomainError]] for errors. */
  def liftTo[F[_], B >: A](implicit F: ApplicativeThrow[F]): F[B] = this match {
    case ValkeyResponse.Ok(value)  => F.pure(value)
    case ValkeyResponse.Err(error) =>
      F.raiseError(
        new ValkeyResponse.ValkeyDomainError(error)
      )
  }
}

object ValkeyResponse {

  /** Successful command result carrying the decoded value. */
  case class Ok[A](value: A) extends ValkeyResponse[A]

  /** Domain error returned by the Valkey server, wrapping a typed [[ValkeyError]]. */
  case class Err(error: ValkeyError) extends ValkeyResponse[Nothing]

  def ok[A](a: A): ValkeyResponse[A] = Ok(a)
  def err(e: ValkeyError): ValkeyResponse[Nothing] = Err(e)

  /** Exception wrapper that lifts a [[ValkeyError]] into MonadThrow / ApplicativeThrow. */
  final class ValkeyDomainError(val error: ValkeyError)
      extends RuntimeException(error.message)

  object ValkeyDomainError {
    def unapply(e: Throwable): Option[ValkeyError] = e match {
      case vde: ValkeyDomainError => Some(vde.error)
      case _                      => None
    }
  }

  implicit val valkeyResponseFunctor: Functor[ValkeyResponse] =
    new Functor[ValkeyResponse] {
      def map[A, B](fa: ValkeyResponse[A])(f: A => B): ValkeyResponse[B] =
        fa.map(f)
    }

  implicit val valkeyResponseMonad: Monad[ValkeyResponse] =
    new Monad[ValkeyResponse] {
      def pure[A](a: A): ValkeyResponse[A] = Ok(a)
      def flatMap[A, B](fa: ValkeyResponse[A])(
          f: A => ValkeyResponse[B]
      ): ValkeyResponse[B] = fa.flatMap(f)
      def tailRecM[A, B](
          a: A
      )(f: A => ValkeyResponse[Either[A, B]]): ValkeyResponse[B] =
        f(a) match {
          case Ok(Left(next)) => tailRecM(next)(f)
          case Ok(Right(b))   => Ok(b)
          case e @ Err(_)     => e
        }
    }

  implicit val valkeyResponseTraverse: Traverse[ValkeyResponse] =
    new Traverse[ValkeyResponse] {
      def traverse[G[_]: Applicative, A, B](
          fa: ValkeyResponse[A]
      )(f: A => G[B]): G[ValkeyResponse[B]] =
        fa match {
          case Ok(a)      => Applicative[G].map(f(a))(Ok(_))
          case e @ Err(_) => Applicative[G].pure(e)
        }
      def foldLeft[A, B](fa: ValkeyResponse[A], b: B)(f: (B, A) => B): B =
        fa match {
          case Ok(a)  => f(b, a)
          case Err(_) => b
        }
      def foldRight[A, B](fa: ValkeyResponse[A], lb: Eval[B])(
          f: (A, Eval[B]) => Eval[B]
      ): Eval[B] =
        fa match {
          case Ok(a)  => f(a, lb)
          case Err(_) => lb
        }
    }

  implicit val valkeyResponseMonadError
      : MonadError[ValkeyResponse, ValkeyError] =
    new MonadError[ValkeyResponse, ValkeyError] {
      def pure[A](a: A): ValkeyResponse[A] = Ok(a)
      def flatMap[A, B](fa: ValkeyResponse[A])(
          f: A => ValkeyResponse[B]
      ): ValkeyResponse[B] = fa.flatMap(f)
      def tailRecM[A, B](
          a: A
      )(f: A => ValkeyResponse[Either[A, B]]): ValkeyResponse[B] =
        f(a) match {
          case Ok(Left(next)) => tailRecM(next)(f)
          case Ok(Right(b))   => Ok(b)
          case e @ Err(_)     => e
        }
      def raiseError[A](e: ValkeyError): ValkeyResponse[A] = Err(e)
      def handleErrorWith[A](
          fa: ValkeyResponse[A]
      )(f: ValkeyError => ValkeyResponse[A]): ValkeyResponse[A] =
        fa match {
          case ok @ Ok(_) => ok
          case Err(e)     => f(e)
        }
    }
}
