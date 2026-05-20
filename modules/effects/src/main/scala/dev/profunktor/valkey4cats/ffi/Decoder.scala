package dev.profunktor.valkey4cats.ffi

import dev.profunktor.valkey4cats.codec.Codec
import java.lang.foreign.MemorySegment

/** A command bundled with its decoder — the type parameter A is the response type.
  * This makes it impossible to pair a command with the wrong decoder at compile time.
  */
private[valkey4cats] final class Cmd[A](val ordinal: CmdOrdinal, val decode: MemorySegment => A)

private[valkey4cats] object Cmd:
  def apply[A](ordinal: CmdOrdinal, decode: MemorySegment => A): Cmd[A] = new Cmd(ordinal, decode)

/** Fixed decoders for commands with codec-independent return types. */
private[valkey4cats] object Decode:
  val unit: MemorySegment => Unit = ResponseParser.decodeUnit
  val long: MemorySegment => Long = ResponseParser.decodeLong
  val double: MemorySegment => Double = ResponseParser.decodeDouble
  val boolean: MemorySegment => Boolean = ResponseParser.decodeBoolean
  val string: MemorySegment => String = ResponseParser.decodeString
  val optionalLong: MemorySegment => Option[Long] = ResponseParser.decodeOptionalLong
  val mapStringString: MemorySegment => Map[String, String] = ResponseParser.decodeMapStringString

  def optional[V](codec: Codec[V]): MemorySegment => Option[V] = ResponseParser.decodeOptional(_, codec)
  def list[V](codec: Codec[V]): MemorySegment => List[V] = ResponseParser.decodeList(_, codec)
  def optionalList[V](codec: Codec[V]): MemorySegment => List[Option[V]] = ResponseParser.decodeOptionalList(_, codec)
  def set[V](codec: Codec[V]): MemorySegment => Set[V] = ResponseParser.decodeSet(_, codec)
  def map[K, V](kc: Codec[K], vc: Codec[V]): MemorySegment => Map[K, V] = ResponseParser.decodeMap(_, kc, vc)
  def value[V](codec: Codec[V]): MemorySegment => V = seg => codec.decode(ResponseParser.decodeBytes(seg))
