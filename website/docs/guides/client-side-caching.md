---
sidebar_position: 1
title: Client-Side Caching
---

# Client-Side Caching

Valkey4Cats supports client-side caching via Valkey Glide's Rust core. This is a purely local, TTL-based cache — responses to read commands are cached in-process and evicted when their TTL expires or the cache reaches capacity. There is no server-push invalidation; staleness is bounded by the configured TTL.

## Configuration

Enable client-side caching by using the `*Cached` factory methods with a `ClientSideCacheConfig`:

```scala
import cats.effect.*
import com.comcast.ip4s.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.*

import scala.concurrent.duration.*

given Log[IO] = Log.Stdout.instance[IO]

val cacheConfig = ClientSideCacheConfig(
  maxCacheKb = 2048,          // 2MB max cache size
  entryTtl = 5.minutes,       // entries expire after 5 minutes
  evictionPolicy = CacheEvictionPolicy.LRU,
  enableMetrics = true
).toOption.get

// Returns CachedValkeyCommands which exposes cache metrics at the type level
Valkey[IO].utf8Cached("valkey://localhost:6379", cacheConfig).use { valkey =>
  for
    _ <- valkey.set("key", "value")
    v <- valkey.get("key")        // served from cache on second call
    rate <- valkey.cacheHitRate   // only available on CachedValkeyCommands
  yield ()
}
```

You can also use `fromConfigCached` for full control over the client configuration:

```scala
val config = ValkeyClientConfig.localhost

Valkey[IO].fromConfigCached[String, String](config, cacheConfig).use { valkey =>
  // valkey: CachedValkeyCommands[IO, String, String]
  valkey.set("k", "v") *> valkey.get("k")
}
```

:::note Type safety
The `*Cached` methods return `CachedValkeyCommands[F, K, V]` which extends `ValkeyCommands` with `CacheMetrics`. If you use the regular factory methods (e.g. `utf8`, `fromConfig`), cache metric methods are not available at compile time — preventing accidental calls when caching is not enabled.
:::

### Configuration options

| Parameter | Description | Default |
|-----------|-------------|---------|
| `maxCacheKb` | Maximum cache size in kilobytes (must be > 0) | Required |
| `entryTtl` | Time-to-live for cached entries (must be >= 1ms) | Required |
| `evictionPolicy` | `LRU` (least recently used) or `LFU` (least frequently used) | `LRU` |
| `enableMetrics` | Whether to track hit/miss statistics | `false` |

## How it works

1. When a client issues a cacheable read command (`GET`, `HGETALL`, `SMEMBERS`), the Glide Rust core caches the response locally
2. Subsequent reads for the same key return the cached value without a network round-trip
3. Cached entries are evicted when their TTL expires (checked lazily on access)
4. If the cache exceeds `maxCacheKb`, entries are evicted according to the configured policy (LRU or LFU)
5. Nil responses are not cached; entries exceeding `maxCacheKb` are silently skipped

This happens transparently — your application code doesn't change. Reads that hit the local cache bypass the network entirely.

:::caution Staleness
Since there is no server-push invalidation, cached data may be stale for up to `entryTtl`. Choose a TTL that balances latency savings against your application's freshness requirements.
:::

## Cache metrics

When `enableMetrics = true`, you can query cache performance via the `CacheMetrics` trait (available on `CachedValkeyCommands`):

```scala
Valkey[IO].utf8Cached("valkey://localhost:6379", cacheConfig).use { valkey =>
  for
    hitRate     <- valkey.cacheHitRate
    missRate    <- valkey.cacheMissRate
    entries     <- valkey.cacheEntryCount
    evictions   <- valkey.cacheEvictions
    expirations <- valkey.cacheExpirations
    lookups     <- valkey.cacheTotalLookups
    _           <- IO.println(s"Hit rate: $hitRate%, entries: $entries, lookups: $lookups")
  yield ()
}
```

### Available metrics

| Method | Return type | Description |
|--------|-------------|-------------|
| `cacheHitRate` | `F[Double]` | Cache hit rate as a percentage (0.0 to 100.0) |
| `cacheMissRate` | `F[Double]` | Cache miss rate as a percentage (0.0 to 100.0) |
| `cacheEntryCount` | `F[Long]` | Current number of entries in the cache |
| `cacheEvictions` | `F[Long]` | Total entries evicted due to capacity limits |
| `cacheExpirations` | `F[Long]` | Total entries expired due to TTL |
| `cacheTotalLookups` | `F[Long]` | Total cache lookup operations (hits + misses) |

:::info Metrics availability
`cacheEntryCount` is available whenever caching is enabled. The other five metrics require `enableMetrics = true` in the config — if metrics are disabled, calling them will result in a failed effect.
:::

## Cluster support

Client-side caching works with both standalone and cluster configurations:

```scala
import dev.profunktor.valkey4cats.model.*
import scala.concurrent.duration.*

val cacheConfig = ClientSideCacheConfig(
  maxCacheKb = 4096,
  entryTtl = 10.minutes
).toOption.get

// Cluster with caching
Valkey[IO].fromClusterConfigCached[String, String](
  ValkeyClusterConfig.builder(host"node1", port"7000"),
  cacheConfig
).use { valkey =>
  // Each client gets its own independent cache instance
  valkey.set("k", "v") *> valkey.get("k")
}
```

In cluster mode, each client adapter maintains its own local cache.

## Disabling caching

If you have a config with caching and want to create a non-cached client, use the regular factory methods — they don't expose cache metrics regardless of the underlying config:

```scala
// Regular factory — no CacheMetrics on the type
Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  // valkey: ValkeyCommands[IO, String, String]
  // valkey.cacheHitRate — compile error!
  valkey.set("k", "v")
}
```
