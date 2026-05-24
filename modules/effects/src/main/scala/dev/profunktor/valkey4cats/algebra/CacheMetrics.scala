package dev.profunktor.valkey4cats.algebra

/** Client-side cache metrics (local Rust-layer lookups, not Valkey commands). */
trait CacheMetrics[F[_]] {
  def cacheHitRate: F[Double]
  def cacheMissRate: F[Double]
  def cacheEntryCount: F[Long]
  def cacheEvictions: F[Long]
  def cacheExpirations: F[Long]
  def cacheTotalLookups: F[Long]
}
