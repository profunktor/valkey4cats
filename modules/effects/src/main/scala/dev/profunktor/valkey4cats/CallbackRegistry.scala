package dev.profunktor.valkey4cats

import cats.effect.{Async, Deferred, Resource}
import cats.effect.std.Dispatcher
import dev.profunktor.valkey4cats.ffi.GlideFfi
import java.lang.foreign.{Arena, Linker, MemorySegment}
import java.lang.invoke.{MethodHandles, MethodType}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private[valkey4cats] final class CallbackRegistry[F[_]] private (
    dispatcher: Dispatcher[F],
    arena: Arena
)(using F: Async[F]):
  import CallbackRegistry.*

  private val nextId = new AtomicLong(0L)
  private val pending = new ConcurrentHashMap[Long, Deferred[F, Either[NativeError, MemorySegment]]]()
  private val canceled = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap[Long, java.lang.Boolean]())

  def register: F[(Long, Deferred[F, Either[NativeError, MemorySegment]])] =
    F.flatMap(Deferred[F, Either[NativeError, MemorySegment]]) { d =>
      val id = nextId.getAndIncrement()
      pending.put(id, d)
      F.pure((id, d))
    }

  def remove(callbackId: Long): Unit =
    val _ = pending.remove(callbackId)

  def markCanceled(callbackId: Long): Unit =
    val _ = canceled.add(callbackId)

  def pendingCount: Int = pending.size()

  @scala.annotation.nowarn("msg=unused private member")
  private def onSuccess(callbackId: Long, responsePtr: MemorySegment): Unit =
    val d = pending.remove(callbackId)
    if d != null then
      if canceled.remove(callbackId) then
        // Consumer was canceled — free the native response directly
        GlideFfi.freeCommandResponse.invoke(responsePtr)
      else
        dispatcher.unsafeRunAndForget(d.complete(Right(responsePtr)))

  @scala.annotation.nowarn("msg=unused private member")
  private def onFailure(callbackId: Long, errorMsgPtr: MemorySegment, errorType: Int): Unit =
    val d = pending.remove(callbackId)
    if d != null then
      val msg = errorMsgPtr.reinterpret(MaxErrorMessageBytes).getString(0, StandardCharsets.UTF_8)
      val err = NativeError(ffi.RequestErrorType.fromCode(errorType), msg)
      dispatcher.unsafeRunAndForget(d.complete(Left(err)))

  val successStub: MemorySegment =
    val mh = MethodHandles.lookup().bind(this, "onSuccess",
      MethodType.methodType(classOf[Unit], classOf[Long], classOf[MemorySegment]))
    Linker.nativeLinker().upcallStub(mh, ffi.GlideFfi.successCallbackDescriptor, arena)

  val failureStub: MemorySegment =
    val mh = MethodHandles.lookup().bind(this, "onFailure",
      MethodType.methodType(classOf[Unit], classOf[Long], classOf[MemorySegment], classOf[Int]))
    Linker.nativeLinker().upcallStub(mh, ffi.GlideFfi.failureCallbackDescriptor, arena)

private[valkey4cats] object CallbackRegistry:

  private val MaxErrorMessageBytes: Long = 65_536L

  final case class NativeError(errorType: ffi.RequestErrorType, message: String)

  def resource[F[_]: Async]: Resource[F, CallbackRegistry[F]] =
    for
      dispatcher <- Dispatcher.parallel[F]
      arena      <- Resource.fromAutoCloseable(Async[F].delay(Arena.ofShared()))
    yield new CallbackRegistry[F](dispatcher, arena)
