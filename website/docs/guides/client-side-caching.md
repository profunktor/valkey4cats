---
sidebar_position: 1
title: Client-Side Caching
---

# Client-Side Caching

Valkey4Cats supports client-side caching via Valkey Glide's Rust core. This is a purely local, TTL-based cache — responses to read commands are cached in-process and evicted when their TTL expires or the cache reaches capacity. There is no server-push invalidation; staleness is bounded by the configured TTL.

## Configuration

Enable client-side caching by adding a `ClientSideCacheConfig` to your client configuration:

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

val commands = ValkeyClientConfig.builder
  .withClientSideCache(cacheConfig)
```

### Configuration options

| Parameter | Description | Default |
|-----------|-------------|---------|
| `maxCacheKb` | Maximum cache size in kilobytes (must be > 0) | Required |
| `entryTtl` | Time-to-live for cached entries (must be > 0) | Required |
| `evictionPolicy` | `LRU` (least recently used) or `LFU` (least frequently used) | `LRU` |
| `enableMetrics` | Whether to track hit/miss statistics | `false` |

## How it works

1. When a client issues a read command, the Glide Rust core caches the response locally
2. Subsequent reads for the same key return the cached value without a network round-trip
3. Cached entries are evicted when their TTL expires
4. If the cache exceeds `maxCacheKb`, entries are evicted according to the configured policy (LRU or LFU)

This happens transparently — your application code doesn't change. Reads that hit the local cache bypass the network entirely.

:::caution Staleness
Since there is no server-push invalidation, cached data may be stale for up to `entryTtl`. Choose a TTL that balances latency savings against your application's freshness requirements.
:::

## Cache metrics

When `enableMetrics = true`, you can query cache performance:

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    hitRate  <- valkey.cacheHitRate
    hits     <- valkey.cacheHits
    misses   <- valkey.cacheMisses
    size     <- valkey.cacheSize
    _        <- IO.println(s"Hit rate: $hitRate, Hits: $hits, Misses: $misses, Size: $size")
  yield ()
}
```

### Available metrics

| Method | Return type | Description |
|--------|-------------|-------------|
| `cacheHitRate` | `F[Double]` | Ratio of cache hits to total requests (0.0 to 1.0) |
| `cacheHits` | `F[Long]` | Total number of cache hits |
| `cacheMisses` | `F[Long]` | Total number of cache misses |
| `cacheSize` | `F[Long]` | Current number of entries in cache |
| `cacheTotalRequests` | `F[Long]` | Total requests served (hits + misses) |
| `cacheTotalInvalidations` | `F[Long]` | Total entries evicted (TTL expiry + capacity eviction) |

## Cluster support

Client-side caching works with both standalone and cluster configurations:

```scala
import dev.profunktor.valkey4cats.model.*
import scala.concurrent.duration.*

val cacheConfig = ClientSideCacheConfig(
  maxCacheKb = 4096,
  entryTtl = 10.minutes
).toOption.get

// Works with cluster configs too
val clusterConfig = ValkeyClusterConfig(
  addresses = List(/* ... */)
).map(_.withClientSideCache(cacheConfig))
```

In cluster mode, each client adapter maintains its own local cache.

## Disabling caching

```scala
val configWithoutCache = existingConfig.withoutClientSideCache
```
