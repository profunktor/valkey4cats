package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.algebra.CacheMetrics

/** Valkey commands with client-side cache metrics.
  *
  * Returned only by factory methods that explicitly enable client-side caching
  * (e.g. [[Valkey.ValkeyPartiallyApplied.utf8Cached]], [[Valkey.ValkeyPartiallyApplied.fromConfigCached]]).
  * Extends [[ValkeyCommands]] with [[CacheMetrics]], making metric methods available
  * only when caching is confirmed at construction time.
  */
trait CachedValkeyCommands[F[_], K, V]
    extends ValkeyCommands[F, K, V]
    with CacheMetrics[F]
