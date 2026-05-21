package dev.profunktor.valkey4cats.benchmarks

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import dev.profunktor.valkey4cats.{Valkey, ValkeyCommands}
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse
import org.openjdk.jmh.annotations.*
import scala.compiletime.uninitialized
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 3)
@Fork(1)
class CommandBenchmark:

  given Log[IO] = Log.NoOp.instance[IO]

  private var valkey: ValkeyCommands[IO, String, String] = uninitialized
  private var cleanup: IO[Unit] = uninitialized
  private val invocationCount = new AtomicLong(0L)

  private val indices10 = (1 to 10).toList
  private val indices100 = (1 to 100).toList

  private def unwrap[A](r: ValkeyResponse[A]): A = r.fold(
    err => throw new RuntimeException(s"Unexpected Valkey error: $err"),
    identity
  )

  @Setup(Level.Trial)
  def setup(): Unit =
    val uri = sys.env.getOrElse("VALKEY_BENCH_URI", "valkey://localhost:6379")
    val (client, release) = Valkey[IO].utf8(uri).allocated.unsafeRunSync()
    valkey = client
    cleanup = release
    // Pre-populate keys for GET benchmarks
    indices10.traverse_(i => client.set(s"bench:batch:$i", "v").void).unsafeRunSync()

  @TearDown(Level.Trial)
  def teardown(): Unit =
    cleanup.unsafeRunSync()

  @Benchmark
  def setGet(): String =
    unwrap(
      (valkey.set("bench:key", "value") *> valkey.get("bench:key"))
        .unsafeRunSync()
    ).getOrElse("")

  @Benchmark
  def setBatch10(): Unit =
    indices10.traverse_(i => valkey.set(s"bench:batch:$i", "v").void)
      .unsafeRunSync()

  @Benchmark
  def getBatch10(): Unit =
    indices10.traverse_(i => valkey.get(s"bench:batch:$i").void)
      .unsafeRunSync()

  @Benchmark
  def parallelSet100(): Unit =
    indices100.parTraverse_(i => valkey.set(s"bench:par:$i", "v").void)
      .unsafeRunSync()

  @Benchmark
  def incrDecr(): Unit =
    (valkey.incr("bench:counter") *> valkey.decr("bench:counter")).void
      .unsafeRunSync()

  @Benchmark
  def hsetHget(): Unit =
    (valkey.hset("bench:hash", Map("field1" -> "val1")) *>
      valkey.hget("bench:hash", "field1")).void
      .unsafeRunSync()

  @Benchmark
  def lpushLpop(): Unit =
    (valkey.lpush("bench:list", "item") *>
      valkey.lpop("bench:list")).void
      .unsafeRunSync()

  @Benchmark
  def saddSmembers(): Unit =
    val key = s"bench:set:${invocationCount.getAndIncrement()}"
    (valkey.sadd(key, "a", "b", "c") *>
      valkey.smembers(key) *>
      valkey.del(key)).void
      .unsafeRunSync()
