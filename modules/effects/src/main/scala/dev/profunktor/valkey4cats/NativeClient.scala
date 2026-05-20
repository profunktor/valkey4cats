package dev.profunktor.valkey4cats

import cats.effect.{Async, Resource}
import dev.profunktor.valkey4cats.ffi.{GlideFfi, NativeArena}
import java.lang.foreign.{Arena, MemorySegment, ValueLayout}
import java.nio.charset.StandardCharsets

private[valkey4cats] final class NativeClient[F[_]] private (
    val handle: MemorySegment,
    val registry: CallbackRegistry[F]
)

private[valkey4cats] object NativeClient:

  private val MaxErrorMessageBytes: Long = 65_536L

  def standalone[F[_]: Async](configBytes: Array[Byte]): Resource[F, NativeClient[F]] =
    for
      registry <- CallbackRegistry.resource[F]
      arena    <- Resource.fromAutoCloseable(Async[F].delay(Arena.ofShared()))
      client   <- Resource.make(connect[F](configBytes, registry, arena))(release[F])
    yield client

  def cluster[F[_]: Async](configBytes: Array[Byte]): Resource[F, NativeClient[F]] =
    standalone[F](configBytes)

  private def connect[F[_]](
      configBytes: Array[Byte],
      registry: CallbackRegistry[F],
      arena: Arena
  )(using F: Async[F]): F[NativeClient[F]] = F.delay {
    val configSeg = NativeArena.allocBytes(arena, configBytes)

    val clientType = arena.allocate(GlideFfi.CLIENT_TYPE_LAYOUT)
    clientType.set(ValueLayout.JAVA_LONG, 0, 0L)
    clientType.set(ValueLayout.ADDRESS, 8, registry.successStub)
    clientType.set(ValueLayout.ADDRESS, 16, registry.failureStub)

    val nullPubSub = MemorySegment.NULL

    val responsePtr = GlideFfi.createClient.invoke(
      configSeg,
      configBytes.length.toLong,
      clientType,
      nullPubSub
    ).asInstanceOf[MemorySegment]

    val response = responsePtr.reinterpret(GlideFfi.CONNECTION_RESPONSE_LAYOUT.byteSize())
    val connPtr = response.get(ValueLayout.ADDRESS, 0)
    val errPtr = response.get(ValueLayout.ADDRESS, 8)

    // Read error message BEFORE freeing — errPtr may be owned by the response struct
    val errMsg =
      if errPtr != MemorySegment.NULL then
        Some(errPtr.reinterpret(MaxErrorMessageBytes).getString(0, StandardCharsets.UTF_8))
      else None

    GlideFfi.freeConnectionResponse.invoke(responsePtr)

    errMsg.foreach(msg => throw new RuntimeException(s"Failed to create Valkey client: $msg"))

    new NativeClient[F](connPtr, registry)
  }

  private def release[F[_]](client: NativeClient[F])(using F: Async[F]): F[Unit] = F.delay {
    GlideFfi.closeClient.invoke(client.handle)
  }
