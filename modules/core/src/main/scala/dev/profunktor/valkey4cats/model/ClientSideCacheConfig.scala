package dev.profunktor.valkey4cats.model

import cats.ApplicativeThrow
import glide.api.models.configuration.{ClientSideCache => GlideClientSideCache}

import scala.concurrent.duration.FiniteDuration

/** Configuration for Glide's client-side cache (local, TTL-based, Rust-backed).
  *
  * Only `GET`, `HGETALL`, and `SMEMBERS` responses are cached.
  * Entries expire lazily (checked on access) and there is no server-push invalidation.
  *
  * Use the validated `apply` or `make` constructors on the companion object.
  */
sealed abstract class ClientSideCacheConfig {
  def maxCacheKb: Long
  def entryTtl: FiniteDuration
  def evictionPolicy: CacheEvictionPolicy
  def enableMetrics: Boolean

  private[valkey4cats] def toGlide: GlideClientSideCache =
    GlideClientSideCache
      .builder()
      .maxCacheKb(maxCacheKb)
      .entryTtlMs(entryTtl.toMillis)
      .evictionPolicy(evictionPolicy.toGlide)
      .enableMetrics(enableMetrics)
      .build()
}

object ClientSideCacheConfig {

  private final case class Impl(
      maxCacheKb: Long,
      entryTtl: FiniteDuration,
      evictionPolicy: CacheEvictionPolicy,
      enableMetrics: Boolean
  ) extends ClientSideCacheConfig

  /** Create a validated cache config.
    *
    * @param maxCacheKb maximum cache size in kilobytes (must be > 0)
    * @param entryTtl time-to-live per entry; minimum 1 millisecond (sub-ms rejected since Glide uses ms granularity)
    * @param evictionPolicy LRU or LFU eviction when cache is full
    * @param enableMetrics enable hit/miss/eviction counters (required for most CacheMetrics methods)
    */
  def apply(
      maxCacheKb: Long,
      entryTtl: FiniteDuration,
      evictionPolicy: CacheEvictionPolicy = CacheEvictionPolicy.LRU,
      enableMetrics: Boolean = false
  ): Either[String, ClientSideCacheConfig] =
    if (maxCacheKb <= 0) Left("maxCacheKb must be positive")
    else if (entryTtl.toMillis <= 0) Left("entryTtl must be at least 1 millisecond")
    else Right(Impl(maxCacheKb, entryTtl, evictionPolicy, enableMetrics))

  def make[F[_]: ApplicativeThrow](
      maxCacheKb: Long,
      entryTtl: FiniteDuration,
      evictionPolicy: CacheEvictionPolicy = CacheEvictionPolicy.LRU,
      enableMetrics: Boolean = false
  ): F[ClientSideCacheConfig] =
    ApplicativeThrow[F].fromEither(
      apply(maxCacheKb, entryTtl, evictionPolicy, enableMetrics)
        .left.map(msg => new IllegalArgumentException(msg))
    )
}
