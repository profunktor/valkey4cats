package dev.profunktor.valkey4cats

import cats.effect.IO
import cats.syntax.all.*
import scala.concurrent.duration.*

class NativeMemorySoakSuite extends ValkeyTestSuite {

  @scala.annotation.nowarn("msg=deprecated")
  override def munitTimeout: Duration = 10.minutes

  private val soakEnabled: Boolean = sys.env.contains("VALKEY_SOAK_TESTS")

  override def munitTests(): Seq[munit.Test] =
    if soakEnabled then super.munitTests()
    else Seq.empty

  private def rssMB: Long =
    val pid = ProcessHandle.current().pid()
    val rt = Runtime.getRuntime
    val proc = rt.exec(Array("ps", "-o", "rss=", "-p", pid.toString))
    val output = new String(proc.getInputStream.readAllBytes()).trim
    proc.waitFor()
    output.toLong / 1024 // KB → MB

  private def pendingCount(valkey: ValkeyCommands[IO, String, String]): Int =
    valkey.asInstanceOf[NativeValkey[IO, String, String]].pendingCallbackCount

  test("1M commands: RSS stays bounded and callbacks drain") {
    val totalCommands = 1_000_000
    val batchSize = 10_000
    val maxRssGrowthMB = 200L

    Valkey[IO].utf8(valkeyUri).use { valkey =>
      for {
        baselineRss <- IO(rssMB)
        _ <- IO.println(s"[soak] baseline RSS: ${baselineRss}MB, running $totalCommands commands...")

        _ <- (1 to (totalCommands / batchSize)).toList.traverse_ { batch =>
          val offset = (batch - 1) * batchSize
          (1 to batchSize).toList.traverse_ { i =>
            val key = s"soak-${offset + i}"
            valkey.set(key, "x") *> valkey.get(key) *> valkey.del(key)
          } *> IO.whenA(batch % 10 == 0) {
            IO(rssMB).flatMap(rss =>
              IO.println(s"[soak] batch $batch/${totalCommands / batchSize}: RSS=${rss}MB, pending=${pendingCount(valkey)}")
            )
          }
        }

        _ <- IO.sleep(200.millis)
        finalRss <- IO(rssMB)
        pending <- IO(pendingCount(valkey))
        growth = finalRss - baselineRss

        _ <- IO.println(s"[soak] done: baseline=${baselineRss}MB, final=${finalRss}MB, growth=${growth}MB, pending=$pending")
      } yield {
        assert(pending == 0, s"Expected 0 pending callbacks after ${totalCommands} commands, got $pending")
        assert(growth < maxRssGrowthMB, s"RSS grew by ${growth}MB (limit: ${maxRssGrowthMB}MB) — possible native memory leak")
      }
    }
  }

  test("1M commands with cancellation pressure: no native memory leak") {
    val totalOps = 1_000_000
    val batchSize = 10_000
    val maxRssGrowthMB = 250L

    Valkey[IO].utf8(valkeyUri).use { valkey =>
      for {
        _ <- (1 to 1000).toList.traverse_(i => valkey.set(s"soak-cancel-$i", "v"))
        baselineRss <- IO(rssMB)
        _ <- IO.println(s"[soak-cancel] baseline RSS: ${baselineRss}MB, running $totalOps ops with cancellation...")

        _ <- (1 to (totalOps / batchSize)).toList.traverse_ { batch =>
          (1 to batchSize).toList.traverse_ { i =>
            val key = s"soak-cancel-${(i % 1000) + 1}"
            if i % 5 == 0 then valkey.get(key).start.flatMap(_.cancel)
            else valkey.get(key).void
          } *> IO.whenA(batch % 10 == 0) {
            IO(rssMB).flatMap(rss =>
              IO.println(s"[soak-cancel] batch $batch/${totalOps / batchSize}: RSS=${rss}MB, pending=${pendingCount(valkey)}")
            )
          }
        }

        _ <- IO.sleep(500.millis)
        finalRss <- IO(rssMB)
        pending <- IO(pendingCount(valkey))
        growth = finalRss - baselineRss

        _ <- IO.println(s"[soak-cancel] done: baseline=${baselineRss}MB, final=${finalRss}MB, growth=${growth}MB, pending=$pending")
        _ <- (1 to 1000).toList.traverse_(i => valkey.del(s"soak-cancel-$i"))
      } yield {
        assert(pending == 0, s"Expected 0 pending callbacks, got $pending")
        assert(growth < maxRssGrowthMB, s"RSS grew by ${growth}MB (limit: ${maxRssGrowthMB}MB) — possible native memory leak under cancellation")
      }
    }
  }
}
