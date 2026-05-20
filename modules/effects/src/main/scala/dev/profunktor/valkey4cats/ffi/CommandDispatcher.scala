package dev.profunktor.valkey4cats.ffi

import cats.effect.Async
import cats.effect.syntax.all.*
import cats.syntax.all.*
import dev.profunktor.valkey4cats.CallbackRegistry
import dev.profunktor.valkey4cats.model.{ValkeyError, ValkeyResponse}
import java.lang.foreign.{Arena, MemorySegment}

/** Encapsulates the register → dispatch → await → decode → free lifecycle.
  * Abstracted so tests can substitute a mock that tracks FFI interactions.
  */
private[valkey4cats] trait CommandDispatcher[F[_]]:
  def exec[A](cmd: Cmd[A], args: Array[Array[Byte]]): F[ValkeyResponse[A]]

private[valkey4cats] object CommandDispatcher:

  val MaxArgBytes: Int = 512 * 1024 * 1024 // 512 MB

  def native[F[_]](
      clientHandle: MemorySegment,
      registry: CallbackRegistry[F]
  )(using F: Async[F]): CommandDispatcher[F] = new CommandDispatcher[F]:

    private def dispatch(callbackId: Long, cmdType: CmdOrdinal, args: Array[Array[Byte]]): F[Unit] =
      F.delay {
        var i = 0
        while i < args.length do
          if args(i).length > MaxArgBytes then
            throw new IllegalArgumentException(
              s"Argument at index $i exceeds maximum size: ${args(i).length} bytes > $MaxArgBytes bytes"
            )
          i += 1

        val arena = Arena.ofConfined()
        try
          val argSegments = args.map(NativeArena.allocBytes(arena, _))
          val argsPtr = NativeArena.allocPointerArray(arena, argSegments)
          val argsLens = NativeArena.allocLengthArray(arena, args.map(_.length.toLong))
          val _ = GlideFfi.command.invoke(
            clientHandle,
            callbackId: java.lang.Long,
            cmdType.toInt: java.lang.Integer,
            args.length.toLong: java.lang.Long,
            argsPtr,
            argsLens,
            MemorySegment.NULL,
            0L: java.lang.Long
          )
        finally arena.close()
      }

    override def exec[A](cmd: Cmd[A], args: Array[Array[Byte]]): F[ValkeyResponse[A]] =
      F.uncancelable { poll =>
        F.flatMap(registry.register) { case (callbackId, deferred) =>
          val await: F[ValkeyResponse[A]] = deferred.get.flatMap {
            case Right(responsePtr) =>
              F.delay(cmd.decode(responsePtr))
                .guarantee(F.delay(GlideFfi.freeCommandResponse.invoke(responsePtr)))
                .map(ValkeyResponse.ok[A])
            case Left(err) =>
              val valkeyErr = err.errorType match
                case RequestErrorType.Timeout    => ValkeyError.Unexpected(err.message, cause = Some(new java.util.concurrent.TimeoutException(err.message)))
                case RequestErrorType.Disconnect => ValkeyError.Unexpected(err.message)
                case _                           => ValkeyError.fromMessage(err.message)
              (ValkeyResponse.err(valkeyErr): ValkeyResponse[A]).pure[F]
          }

          val cleanup = F.delay(registry.remove(callbackId))

          dispatch(callbackId, cmd.ordinal, args)
            .onError(_ => cleanup)
            .flatMap { _ =>
              // After this point, a native callback WILL fire for this callbackId.
              // Only `await` is cancelable (via poll). On cancel: mark as canceled
              // so onSuccess frees the pointer directly.
              poll(await).onCancel(
                F.delay(registry.markCanceled(callbackId)) *>
                  deferred.tryGet.flatMap {
                    case Some(Right(responsePtr)) =>
                      F.delay(GlideFfi.freeCommandResponse.invoke(responsePtr)).void
                    case _ =>
                      // onSuccess will see the canceled mark and free when it arrives.
                      F.unit
                  }
              )
            }
        }
      }
