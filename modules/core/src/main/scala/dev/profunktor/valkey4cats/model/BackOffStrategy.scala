package dev.profunktor.valkey4cats.model

import scala.concurrent.duration.FiniteDuration

/** Reconnection backoff strategy */
sealed trait BackOffStrategy

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
