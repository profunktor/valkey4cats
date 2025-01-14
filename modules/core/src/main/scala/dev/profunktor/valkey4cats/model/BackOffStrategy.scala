package dev.profunktor.valkey4cats.model

import glide.api.models.configuration as G
import scala.concurrent.duration.FiniteDuration

/** Reconnection backoff strategy */
sealed trait BackOffStrategy { self =>

  private[valkey4cats] def toGlide: G.BackoffStrategy =
    self match {
      case BackOffStrategy.FixedDelay(numOfRetries, factor) =>
        G.BackoffStrategy
          .builder()
          .numOfRetries(numOfRetries)
          .factor(factor.toMillis.toInt)
          .build()
      case BackOffStrategy.ExponentialBackoff(
            numOfRetries,
            baseFactor,
            exponentBase,
            jitterPercent
          ) =>
        G.BackoffStrategy
          .builder()
          .numOfRetries(numOfRetries)
          .factor(baseFactor.toMillis.toInt)
          .exponentBase(exponentBase)
          .jitterPercent(jitterPercent)
          .build()
    }
}

object BackOffStrategy {

  /** Fixed delay between reconnection attempts
    *
    * @param numOfRetries Number of retry attempts
    * @param factor Delay between retries
    */
  final case class FixedDelay(
      numOfRetries: Int,
      factor: FiniteDuration
  ) extends BackOffStrategy

  /** Exponential backoff with optional jitter
    *
    * @param numOfRetries Number of retry attempts
    * @param baseFactor Base delay for exponential calculation
    * @param exponentBase Exponent base (default: 2)
   *
    */
  final case class ExponentialBackoff(
      numOfRetries: Int,
      baseFactor: FiniteDuration,
      exponentBase: Int = 2,
      jitterPercent: Int
  ) extends BackOffStrategy
}
