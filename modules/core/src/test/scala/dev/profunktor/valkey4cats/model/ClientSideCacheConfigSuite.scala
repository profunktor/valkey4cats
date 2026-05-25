package dev.profunktor.valkey4cats.model

import scala.concurrent.duration.*
import munit.FunSuite

class ClientSideCacheConfigSuite extends FunSuite {

  test("valid config succeeds") {
    val result = ClientSideCacheConfig(
      maxCacheKb = 1024,
      entryTtl = 60.seconds
    )
    assert(result.isRight)
    val config = result.toOption.get
    assertEquals(config.maxCacheKb, 1024L)
    assertEquals(config.entryTtl, 60.seconds)
    assertEquals(config.evictionPolicy, CacheEvictionPolicy.LRU)
    assertEquals(config.enableMetrics, false)
  }

  test("valid config with LFU and metrics") {
    val result = ClientSideCacheConfig(
      maxCacheKb = 512,
      entryTtl = 30.seconds,
      evictionPolicy = CacheEvictionPolicy.LFU,
      enableMetrics = true
    )
    assert(result.isRight)
    val config = result.toOption.get
    assertEquals(config.evictionPolicy, CacheEvictionPolicy.LFU)
    assertEquals(config.enableMetrics, true)
  }

  test("maxCacheKb must be positive") {
    val result = ClientSideCacheConfig(maxCacheKb = 0, entryTtl = 60.seconds)
    assertEquals(result, Left("maxCacheKb must be positive"))
  }

  test("negative maxCacheKb rejected") {
    val result = ClientSideCacheConfig(maxCacheKb = -1, entryTtl = 60.seconds)
    assertEquals(result, Left("maxCacheKb must be positive"))
  }

  test("entryTtl must be at least 1 millisecond") {
    val result = ClientSideCacheConfig(maxCacheKb = 1024, entryTtl = 0.seconds)
    assertEquals(result, Left("entryTtl must be at least 1 millisecond"))
  }

  test("negative entryTtl rejected") {
    val result = ClientSideCacheConfig(maxCacheKb = 1024, entryTtl = -1.second)
    assertEquals(result, Left("entryTtl must be at least 1 millisecond"))
  }

  test("sub-millisecond entryTtl rejected") {
    val result = ClientSideCacheConfig(maxCacheKb = 1024, entryTtl = 500.micros)
    assertEquals(result, Left("entryTtl must be at least 1 millisecond"))
  }

  test("make[Either] returns Right for valid config") {
    val result = ClientSideCacheConfig.make[Either[Throwable, *]](
      maxCacheKb = 1024,
      entryTtl = 60.seconds
    )
    assert(result.isRight)
  }

  test("make[Either] returns Left for invalid config") {
    val result = ClientSideCacheConfig.make[Either[Throwable, *]](
      maxCacheKb = 0,
      entryTtl = 60.seconds
    )
    assert(result.isLeft)
    assert(result.left.toOption.get.isInstanceOf[IllegalArgumentException])
  }
}
