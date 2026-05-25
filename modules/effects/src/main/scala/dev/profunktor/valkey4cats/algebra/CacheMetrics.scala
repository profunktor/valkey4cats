package dev.profunktor.valkey4cats.algebra

/** Client-side cache metrics.
  *
  * These are local Rust-layer lookups, not Valkey server commands.
  * Available only on [[dev.profunktor.valkey4cats.CachedValkeyCommands]] instances
  * (i.e., clients constructed with explicit caching configuration).
  *
  * All methods except `cacheEntryCount` require `enableMetrics = true` in the
  * [[dev.profunktor.valkey4cats.model.ClientSideCacheConfig]]; calling them
  * when metrics are disabled will result in a failed effect.
  */
trait CacheMetrics[F[_]] {

  /** Cache hit rate as a percentage (0.0 to 100.0).
    * Requires `enableMetrics = true`.
    */
  def cacheHitRate: F[Double]

  /** Cache miss rate as a percentage (0.0 to 100.0).
    * Requires `enableMetrics = true`.
    */
  def cacheMissRate: F[Double]

  /** Current number of entries stored in the local cache.
    * Available whenever caching is enabled (does not require `enableMetrics`).
    */
  def cacheEntryCount: F[Long]

  /** Total number of entries evicted due to capacity limits.
    * Requires `enableMetrics = true`.
    */
  def cacheEvictions: F[Long]

  /** Total number of entries expired due to TTL.
    * Requires `enableMetrics = true`.
    */
  def cacheExpirations: F[Long]

  /** Total cache lookup operations performed (hits + misses).
    * Requires `enableMetrics = true`.
    */
  def cacheTotalLookups: F[Long]
}
