# Client-Side Caching — Design Spec

## Problem

Every `GET`, `HGETALL`, or `SMEMBERS` call makes a network round-trip even when data hasn't changed. Glide 2.4.0 ships a TTL-based client-side cache in the Rust core that eliminates round-trips for cache hits.

## Glide Java API

```
glide.api.models.configuration.ClientSideCache
  .builder()
    .maxCacheKb(long)                    // required, must be > 0
    .entryTtlMs(long)                    // required, must be > 0
    .evictionPolicy(EvictionPolicy)      // LRU (default) | LFU
    .enableMetrics(boolean)              // default false
    .build()                             // side effect: AtomicLong increment for cacheId

// Wired into client config builders:
GlideClientConfiguration.builder().clientSideCache(cache)
GlideClusterClientConfiguration.builder().clientSideCache(cache)

// Metrics on BaseClient (goes through Rust via protobuf):
baseClient.getCacheHitRate()       -> CompletableFuture[Double]
baseClient.getCacheMissRate()      -> CompletableFuture[Double]
baseClient.getCacheEntryCount()    -> CompletableFuture[Long]
baseClient.getCacheEvictions()     -> CompletableFuture[Long]
baseClient.getCacheExpirations()   -> CompletableFuture[Long]
baseClient.getCacheTotalLookups()  -> CompletableFuture[Long]
```

### Behavior

- Cached commands: `GET`, `HGETALL`, `SMEMBERS` only
- TTL-based expiration (lazy — checked on access, not proactively)
- No server-side invalidation (entries may go stale before TTL)
- Nil responses are NOT cached
- Entries exceeding `maxCacheKb` are silently skipped
- Multiple clients can share one `ClientSideCache` instance (via cacheId)

## Scala API

### CacheEvictionPolicy

```scala
package dev.profunktor.valkey4cats.model

sealed trait CacheEvictionPolicy
object CacheEvictionPolicy {
  case object LRU extends CacheEvictionPolicy
  case object LFU extends CacheEvictionPolicy
}
```

### ClientSideCacheConfig

Pure validated config value. The Glide `ClientSideCache` object (which has the AtomicLong side effect) is constructed via `toGlide` inside the effectful client acquisition path (`FutureLift[F].lift(...)`).

```scala
package dev.profunktor.valkey4cats.model

sealed abstract class ClientSideCacheConfig {
  def maxCacheKb: Long
  def entryTtl: FiniteDuration
  def evictionPolicy: CacheEvictionPolicy
  def enableMetrics: Boolean

  private[valkey4cats] def toGlide: glide.api.models.configuration.ClientSideCache
}

object ClientSideCacheConfig {
  def apply(
      maxCacheKb: Long,
      entryTtl: FiniteDuration,
      evictionPolicy: CacheEvictionPolicy = CacheEvictionPolicy.LRU,
      enableMetrics: Boolean = false
  ): Either[String, ClientSideCacheConfig]

  def make[F[_]: ApplicativeThrow](
      maxCacheKb: Long,
      entryTtl: FiniteDuration,
      evictionPolicy: CacheEvictionPolicy = CacheEvictionPolicy.LRU,
      enableMetrics: Boolean = false
  ): F[ClientSideCacheConfig]
}
```

Validation: `maxCacheKb > 0`, `entryTtl >= 1.millisecond` (sub-ms durations are rejected since Glide uses ms granularity).

### CommonConfig Integration

Add `clientSideCache: Option[ClientSideCacheConfig]` to `CommonConfig`. Expose `withClientSideCache` / `withoutClientSideCache` on both `ValkeyClientConfig` and `ValkeyClusterConfig` (delegating to `CommonConfig`).

Wire in each `toGlide`:
```scala
common.clientSideCache.foreach(c => builder.clientSideCache(c.toGlide))
```

### CacheMetrics Algebra

Metrics return plain `F[A]` — these are local Rust-layer lookups, not Valkey commands, and cannot produce a `ValkeyError`.

```scala
package dev.profunktor.valkey4cats.algebra

trait CacheMetrics[F[_]] {
  def cacheHitRate: F[Double]
  def cacheMissRate: F[Double]
  def cacheEntryCount: F[Long]
  def cacheEvictions: F[Long]
  def cacheExpirations: F[Long]
  def cacheTotalLookups: F[Long]
}
```

### Implementation in BaseValkey

```scala
override def cacheHitRate: F[Double] =
  baseClient.getCacheHitRate().futureLift.map(_.doubleValue())
// ... same pattern for others
```

### Usage

```scala
val cacheConfig = ClientSideCacheConfig(
  maxCacheKb = 1024,
  entryTtl = 60.seconds
).fold(sys.error, identity)

val config = ValkeyClientConfig.builder
  .withAddress(host"localhost", port"6379")
  .withClientSideCache(cacheConfig)
```

Sharing across connections:
```scala
val shared = ClientSideCacheConfig(maxCacheKb = 2048, entryTtl = 30.seconds)
  .fold(sys.error, identity)

val standalone = ValkeyClientConfig.builder.withClientSideCache(shared)
val cluster = ValkeyClusterConfig.builder.withClientSideCache(shared)
```

## File Changes

| File | Change |
|------|--------|
| `modules/core/.../model/ClientSideCacheConfig.scala` | New — config + validation + `toGlide` |
| `modules/core/.../model/CacheEvictionPolicy.scala` | New — ADT |
| `modules/core/.../model/CommonConfig.scala` | Add `clientSideCache` field + builder methods |
| `modules/core/.../model/ValkeyClientConfig.scala` | Delegate `withClientSideCache`, wire in `toGlide` |
| `modules/core/.../model/ValkeyClusterConfig.scala` | Same |
| `modules/effects/.../algebra/CacheMetrics.scala` | New — metrics trait |
| `modules/effects/.../BaseValkey.scala` | Implement `CacheMetrics` |
| `modules/effects/.../ValkeyCommands.scala` | Extend `CacheMetrics` |
| `modules/core/src/test/.../ClientSideCacheConfigSuite.scala` | Validation unit tests |

## Verification

1. Unit tests: `ClientSideCacheConfig` rejects `maxCacheKb <= 0` and `entryTtl <= 0`
2. Compile: both config types wire through to Glide builders
3. Integration test: create client with cache, `SET` then `GET` twice, assert `cacheHitRate > 0`
