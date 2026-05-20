package dev.profunktor.valkey4cats.ffi

import cats.effect.{Async, Resource}
import java.lang.foreign.{Arena, MemorySegment, ValueLayout}

private[valkey4cats] object NativeArena:

  def shared[F[_]: Async]: Resource[F, Arena] =
    Resource.fromAutoCloseable(Async[F].delay(Arena.ofShared()))

  def allocBytes(arena: Arena, bytes: Array[Byte]): MemorySegment =
    val seg = arena.allocate(bytes.length.toLong)
    MemorySegment.copy(bytes, 0, seg, ValueLayout.JAVA_BYTE, 0, bytes.length)
    seg

  def allocPointerArray(arena: Arena, segments: Array[MemorySegment]): MemorySegment =
    val arr = arena.allocate(ValueLayout.ADDRESS, segments.length.toLong)
    segments.zipWithIndex.foreach { (seg, i) =>
      arr.setAtIndex(ValueLayout.ADDRESS, i.toLong, seg)
    }
    arr

  def allocLengthArray(arena: Arena, lengths: Array[Long]): MemorySegment =
    val arr = arena.allocate(ValueLayout.JAVA_LONG, lengths.length.toLong)
    lengths.zipWithIndex.foreach { (len, i) =>
      arr.setAtIndex(ValueLayout.JAVA_LONG, i.toLong, len)
    }
    arr
