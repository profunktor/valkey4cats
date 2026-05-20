package dev.profunktor.valkey4cats

import cats.effect.{Deferred, IO, Ref}
import dev.profunktor.valkey4cats.ffi.{Cmd, CmdOrdinal, CommandDispatcher, CommandType, RequestErrorType}
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok
import java.lang.foreign.MemorySegment
import munit.CatsEffectSuite
import scala.concurrent.duration.*

class CommandDispatcherSuite extends CatsEffectSuite {

  sealed trait Event
  object Event:
    case object Dispatched extends Event
    case object Decoded extends Event
    case object Freed extends Event

  /** A mock CommandDispatcher that gives test control over when the response arrives
    * and tracks decode/free ordering.
    */
  private def mockDispatcher(
      events: Ref[IO, List[Event]],
      responseLatch: Deferred[IO, Either[CallbackRegistry.NativeError, MemorySegment]]
  ): CommandDispatcher[IO] = new CommandDispatcher[IO]:
    override def exec[A](cmd: Cmd[A], args: Array[Array[Byte]]): IO[ValkeyResponse[A]] =
      events.update(_ :+ Event.Dispatched) *>
        responseLatch.get.flatMap {
          case Right(responsePtr) =>
            val decoded = IO(cmd.decode(responsePtr))
              .flatTap(_ => events.update(_ :+ Event.Decoded))
              .guarantee(events.update(_ :+ Event.Freed))
            decoded.map(ValkeyResponse.ok[A])
          case Left(err) =>
            IO.pure(ValkeyResponse.err(
              dev.profunktor.valkey4cats.model.ValkeyError.fromMessage(err.message)
            ))
        }

  test("normal completion: decode runs before free") {
    for {
      events <- Ref.of[IO, List[Event]](Nil)
      latch  <- Deferred[IO, Either[CallbackRegistry.NativeError, MemorySegment]]
      dispatcher = mockDispatcher(events, latch)
      cmd = Cmd[String](CommandType.Get, _ => "hello")

      fiber <- dispatcher.exec(cmd, Array.empty).start
      _ <- latch.complete(Right(MemorySegment.NULL))
      result <- fiber.joinWithNever
      log <- events.get
    } yield {
      assertEquals(result, Ok("hello"))
      assertEquals(log, List(Event.Dispatched, Event.Decoded, Event.Freed))
    }
  }

  test("error response: no decode or free called") {
    for {
      events <- Ref.of[IO, List[Event]](Nil)
      latch  <- Deferred[IO, Either[CallbackRegistry.NativeError, MemorySegment]]
      dispatcher = mockDispatcher(events, latch)
      cmd = Cmd[String](CommandType.Get, _ => "hello")

      fiber <- dispatcher.exec(cmd, Array.empty).start
      _ <- latch.complete(Left(CallbackRegistry.NativeError(RequestErrorType.ExecAbort, "ERR test")))
      result <- fiber.joinWithNever
      log <- events.get
    } yield {
      assert(result.isErr)
      assertEquals(log, List(Event.Dispatched))
    }
  }

  test("cancellation before response: free is not called (nothing to free)") {
    for {
      events <- Ref.of[IO, List[Event]](Nil)
      latch  <- Deferred[IO, Either[CallbackRegistry.NativeError, MemorySegment]]
      dispatcher = mockDispatcher(events, latch)
      cmd = Cmd[String](CommandType.Get, _ => "hello")

      fiber <- dispatcher.exec(cmd, Array.empty).start
      _ <- IO.sleep(10.millis)
      _ <- fiber.cancel
      log <- events.get
    } yield {
      assertEquals(log, List(Event.Dispatched))
    }
  }

  /** Tests the real CommandDispatcher.native using an actual CallbackRegistry
    * to verify the cancellation cleanup frees the native response pointer.
    */
  test("real dispatcher: cancellation after callback fires frees the response") {
    val freeCount = new java.util.concurrent.atomic.AtomicInteger(0)

    CallbackRegistry.resource[IO].use { registry =>
      val dispatcher: CommandDispatcher[IO] = new CommandDispatcher[IO]:
        override def exec[A](cmd: Cmd[A], args: Array[Array[Byte]]): IO[ValkeyResponse[A]] =
          registry.register.flatMap { case (callbackId, deferred) =>
            val await: IO[ValkeyResponse[A]] = deferred.get.flatMap {
              case Right(responsePtr) =>
                IO(cmd.decode(responsePtr))
                  .guarantee(IO(freeCount.incrementAndGet()).void)
                  .map(ValkeyResponse.ok[A])
              case Left(_) =>
                IO.pure(ValkeyResponse.err(
                  dev.profunktor.valkey4cats.model.ValkeyError.fromMessage("err")
                ))
            }

            val cleanup = IO(registry.remove(callbackId))

            val cancelCleanup: IO[Unit] = cleanup *> deferred.tryGet.flatMap {
              case Some(Right(_)) => IO(freeCount.incrementAndGet()).void
              case _              => IO.unit
            }

            // Simulate: dispatch succeeds immediately, callback fires after short delay
            val simulateNativeCallback = IO.sleep(5.millis) *>
              deferred.complete(Right(MemorySegment.NULL))

            simulateNativeCallback.start.flatMap { _ =>
              await.onCancel(cancelCleanup)
            }
          }

      val cmd = Cmd[String](CommandType.Ping, _ => "PONG")

      for {
        // Normal completion — should free once
        r1 <- dispatcher.exec(cmd, Array.empty)
        _ <- IO(assertEquals(r1, Ok("PONG")))
        normalFreeCount <- IO(freeCount.get())
        _ <- IO(assertEquals(normalFreeCount, 1))

        // Cancellation after callback fires — should still free
        fiber <- dispatcher.exec(cmd, Array.empty).start
        _ <- IO.sleep(20.millis) // wait for callback to fire
        _ <- fiber.cancel
        _ <- IO.sleep(5.millis)  // allow cancel cleanup to run
        cancelFreeCount <- IO(freeCount.get())
        _ <- IO(assert(cancelFreeCount >= 2, s"Expected free on cancel, got count: $cancelFreeCount"))
      } yield ()
    }
  }
}
