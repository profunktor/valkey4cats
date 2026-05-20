package dev.profunktor.valkey4cats

import cats.effect.IO
import cats.syntax.all.*
import scala.concurrent.duration.*

class MemoryLeakSuite extends ValkeyTestSuite {

  @scala.annotation.nowarn("msg=deprecated")
  override def munitTimeout: Duration = 60.seconds

  private def pendingCount(valkey: ValkeyCommands[IO, String, String]): Int =
    valkey.asInstanceOf[NativeValkey[IO, String, String]].pendingCallbackCount

  private def waitForDrain(
      valkey: ValkeyCommands[IO, String, String],
      maxAttempts: Int,
      interval: FiniteDuration
  ): IO[Int] =
    def loop(remaining: Int): IO[Int] =
      IO(pendingCount(valkey)).flatMap { count =>
        if count == 0 || remaining <= 0 then IO.pure(count)
        else IO.sleep(interval) *> loop(remaining - 1)
      }
    loop(maxAttempts)

  test("callback registry drains to zero after 10K commands") {
    Valkey[IO].utf8(valkeyUri).use { valkey =>
      for {
        _ <- (1 to 10000).toList.traverse_ { i =>
          valkey.set(s"leak-test-$i", s"value-$i") *>
            valkey.get(s"leak-test-$i")
        }
        _ <- IO.sleep(50.millis)
        pending <- IO(pendingCount(valkey))
        _ <- (1 to 10000).toList.traverse_(i => valkey.del(s"leak-test-$i"))
      } yield assertEquals(pending, 0, s"Expected 0 pending callbacks after 20K ops, got $pending")
    }
  }

  test("callback registry drains after mixed cancellations and completions") {
    Valkey[IO].utf8(valkeyUri).use { valkey =>
      for {
        _ <- (1 to 100).toList.traverse_(i => valkey.set(s"leak-cancel-$i", "v"))
        // Rapid fire-and-cancel
        _ <- (1 to 100).toList.traverse_ { i =>
          valkey.get(s"leak-cancel-$i").start.flatMap(_.cancel)
        }
        // Flush with normal commands to ensure native callbacks have fired
        _ <- (1 to 100).toList.traverse_(i => valkey.get(s"leak-cancel-$i"))
        // Wait for background finalizer fibers to complete cleanup
        pending <- waitForDrain(valkey, maxAttempts = 50, interval = 100.millis)
        _ <- (1 to 100).toList.traverse_(i => valkey.del(s"leak-cancel-$i"))
      } yield assert(pending == 0, s"Expected 0 pending callbacks, got $pending")
    }
  }

  test("repeated client creation and destruction does not leak callbacks") {
    (1 to 50).toList.traverse_ { i =>
      Valkey[IO].utf8(valkeyUri).use { valkey =>
        valkey.set(s"lifecycle-$i", "v") *>
          valkey.get(s"lifecycle-$i") *>
          valkey.del(s"lifecycle-$i") *>
          IO.sleep(1.millis) *>
          IO(pendingCount(valkey)).flatMap { pending =>
            IO(assert(pending == 0, s"Client $i has $pending pending callbacks before release"))
          }
      }
    }
  }
}
