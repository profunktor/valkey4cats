package dev.profunktor.valkey4cats.benchmarks

import dev.profunktor.valkey4cats.codec.Codec
import glide.api.models.GlideString
import org.openjdk.jmh.annotations.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 2)
@Fork(1)
class CodecBenchmark:

  private val codec = Codec.utf8Codec
  private val shortString = "hello"
  private val mediumString = "a" * 256
  private val largeString = "b" * 65536
  private val shortGs = GlideString.of(shortString.getBytes(StandardCharsets.UTF_8))
  private val mediumGs = GlideString.of(mediumString.getBytes(StandardCharsets.UTF_8))
  private val largeGs = GlideString.of(largeString.getBytes(StandardCharsets.UTF_8))

  @Benchmark
  def encodeShort(): GlideString = codec.encode(shortString)

  @Benchmark
  def encodeMedium(): GlideString = codec.encode(mediumString)

  @Benchmark
  def encodeLarge(): GlideString = codec.encode(largeString)

  @Benchmark
  def decodeShort(): String = codec.decode(shortGs)

  @Benchmark
  def decodeMedium(): String = codec.decode(mediumGs)

  @Benchmark
  def decodeLarge(): String = codec.decode(largeGs)

  @Benchmark
  def roundTripShort(): String = codec.decode(codec.encode(shortString))

  @Benchmark
  def roundTripLarge(): String = codec.decode(codec.encode(largeString))
