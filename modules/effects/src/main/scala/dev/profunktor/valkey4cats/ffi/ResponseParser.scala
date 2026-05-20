package dev.profunktor.valkey4cats.ffi

import dev.profunktor.valkey4cats.codec.Codec
import java.lang.foreign.{MemorySegment, ValueLayout}
import java.nio.charset.StandardCharsets

/**
 * Parses CommandResponse structs returned from libglide_ffi.
 *
 * CommandResponse layout (repr(C), arm64):
 *   offset 0:  response_type (i32, ResponseType enum)
 *   offset 8:  int_value (i64)
 *   offset 16: float_value (f64)
 *   offset 24: bool_value (bool/i8)
 *   offset 32: string_value (*mut c_char)
 *   offset 40: string_value_len (c_long)
 *   offset 48: array_value (*mut CommandResponse)
 *   offset 56: array_value_len (c_long)
 *   offset 64: map_key (*mut CommandResponse)
 *   offset 72: map_value (*mut CommandResponse)
 *   offset 80: sets_value (*mut CommandResponse)
 *   offset 88: sets_value_len (c_long)
 *   total: 96 bytes
 *
 * ResponseType enum: Null=0, Int=1, Float=2, Bool=3, String=4, Array=5, Map=6, Sets=7, Ok=8, Error=9
 */
private[valkey4cats] object ResponseParser:

  private val RESPONSE_SIZE = 96L

  private object Offset:
    val ResponseType = 0L
    val IntValue = 8L
    val FloatValue = 16L
    val BoolValue = 24L
    val StringValue = 32L
    val StringValueLen = 40L
    val ArrayValue = 48L
    val ArrayValueLen = 56L
    val MapKey = 64L
    val MapValue = 72L
    val SetsValue = 80L
    val SetsValueLen = 88L

  private enum RespType(val tag: Int):
    case Null    extends RespType(0)
    case Int     extends RespType(1)
    case Float   extends RespType(2)
    case Bool    extends RespType(3)
    case StringT extends RespType(4)
    case Array   extends RespType(5)
    case Map     extends RespType(6)
    case Sets    extends RespType(7)
    case Ok      extends RespType(8)
    case Error   extends RespType(9)

  private object RespType:
    private val byTag: scala.Array[RespType] = values.sortBy(_.tag).toArray
    def fromTag(n: Int): RespType =
      if n >= 0 && n < byTag.length then byTag(n)
      else throw new IllegalStateException(s"Unknown response type: $n")

  private def responseType(seg: MemorySegment): RespType =
    RespType.fromTag(seg.get(ValueLayout.JAVA_INT, Offset.ResponseType))

  private def reinterpretResponse(ptr: MemorySegment): MemorySegment =
    ptr.reinterpret(RESPONSE_SIZE)

  def decodeLong(seg: MemorySegment): Long =
    val r = reinterpretResponse(seg)
    r.get(ValueLayout.JAVA_LONG, Offset.IntValue)

  def decodeDouble(seg: MemorySegment): Double =
    val r = reinterpretResponse(seg)
    val rt = responseType(r)
    if rt == RespType.Float then r.get(ValueLayout.JAVA_DOUBLE, Offset.FloatValue)
    else if rt == RespType.StringT then
      new String(decodeBytes(seg), StandardCharsets.UTF_8).toDouble
    else if rt == RespType.Int then r.get(ValueLayout.JAVA_LONG, Offset.IntValue).toDouble
    else if rt == RespType.Null then 0.0d
    else r.get(ValueLayout.JAVA_DOUBLE, Offset.FloatValue)

  def decodeBoolean(seg: MemorySegment): Boolean =
    val r = reinterpretResponse(seg)
    val rt = responseType(r)
    if rt == RespType.Bool then r.get(ValueLayout.JAVA_BYTE, Offset.BoolValue) != 0
    else r.get(ValueLayout.JAVA_LONG, Offset.IntValue) != 0L

  def decodeString(seg: MemorySegment): String =
    new String(decodeBytes(seg), StandardCharsets.UTF_8)

  def decodeBytes(seg: MemorySegment): Array[Byte] =
    val r = reinterpretResponse(seg)
    val rt = responseType(r)
    if rt == RespType.Null then Array.emptyByteArray
    else if rt == RespType.Ok then "OK".getBytes(StandardCharsets.UTF_8)
    else
      val strPtr = r.get(ValueLayout.ADDRESS, Offset.StringValue)
      val strLen = r.get(ValueLayout.JAVA_LONG, Offset.StringValueLen)
      if strPtr == MemorySegment.NULL || strLen <= 0 then Array.emptyByteArray
      else strPtr.reinterpret(strLen).toArray(ValueLayout.JAVA_BYTE)

  def decodeOptional[V](seg: MemorySegment, codec: Codec[V]): Option[V] =
    if seg == MemorySegment.NULL || seg.address() == 0L then None
    else
      val r = reinterpretResponse(seg)
      val rt = responseType(r)
      if rt == RespType.Null then None
      else Some(codec.decode(decodeBytes(seg)))

  def decodeOptionalLong(seg: MemorySegment): Option[Long] =
    if seg == MemorySegment.NULL || seg.address() == 0L then None
    else
      val r = reinterpretResponse(seg)
      val rt = responseType(r)
      if rt == RespType.Null then None
      else Some(r.get(ValueLayout.JAVA_LONG, Offset.IntValue))

  def decodeList[V](seg: MemorySegment, codec: Codec[V]): List[V] =
    val r = reinterpretResponse(seg)
    val rt = responseType(r)
    if rt == RespType.Null then Nil
    else if rt == RespType.Sets then
      val setsPtr = r.get(ValueLayout.ADDRESS, Offset.SetsValue)
      val setsLen = r.get(ValueLayout.JAVA_LONG, Offset.SetsValueLen).toInt
      if setsPtr == MemorySegment.NULL || setsLen <= 0 then Nil
      else
        val arr = setsPtr.reinterpret(setsLen.toLong * RESPONSE_SIZE)
        (0 until setsLen).map { i =>
          val elem = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
          codec.decode(decodeBytes(elem))
        }.toList
    else
      val arrPtr = r.get(ValueLayout.ADDRESS, Offset.ArrayValue)
      val arrLen = r.get(ValueLayout.JAVA_LONG, Offset.ArrayValueLen).toInt
      if arrPtr == MemorySegment.NULL || arrLen <= 0 then Nil
      else
        val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
        (0 until arrLen).map { i =>
          val elem = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
          codec.decode(decodeBytes(elem))
        }.toList

  def decodeOptionalList[V](seg: MemorySegment, codec: Codec[V]): List[Option[V]] =
    val r = reinterpretResponse(seg)
    val rt = responseType(r)
    if rt == RespType.Null then Nil
    else
      val arrPtr = r.get(ValueLayout.ADDRESS, Offset.ArrayValue)
      val arrLen = r.get(ValueLayout.JAVA_LONG, Offset.ArrayValueLen).toInt
      if arrPtr == MemorySegment.NULL || arrLen <= 0 then Nil
      else
        val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
        (0 until arrLen).map { i =>
          val elem = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
          val elemRt = responseType(elem)
          if elemRt == RespType.Null then None
          else Some(codec.decode(decodeBytes(elem)))
        }.toList

  def decodeSet[V](seg: MemorySegment, codec: Codec[V]): Set[V] =
    val r = reinterpretResponse(seg)
    val rt = responseType(r)
    if rt == RespType.Null then Set.empty
    else if rt == RespType.Sets then
      val setsPtr = r.get(ValueLayout.ADDRESS, Offset.SetsValue)
      val setsLen = r.get(ValueLayout.JAVA_LONG, Offset.SetsValueLen).toInt
      if setsPtr == MemorySegment.NULL || setsLen <= 0 then Set.empty
      else
        val arr = setsPtr.reinterpret(setsLen.toLong * RESPONSE_SIZE)
        (0 until setsLen).map { i =>
          val elem = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
          codec.decode(decodeBytes(elem))
        }.toSet
    else // Array fallback
      val arrPtr = r.get(ValueLayout.ADDRESS, Offset.ArrayValue)
      val arrLen = r.get(ValueLayout.JAVA_LONG, Offset.ArrayValueLen).toInt
      if arrPtr == MemorySegment.NULL || arrLen <= 0 then Set.empty
      else
        val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
        (0 until arrLen).map { i =>
          val elem = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
          codec.decode(decodeBytes(elem))
        }.toSet

  def decodeListLong(seg: MemorySegment): List[Long] =
    val r = reinterpretResponse(seg)
    val rt = responseType(r)
    if rt == RespType.Null then Nil
    else
      val arrPtr = r.get(ValueLayout.ADDRESS, Offset.ArrayValue)
      val arrLen = r.get(ValueLayout.JAVA_LONG, Offset.ArrayValueLen).toInt
      if arrPtr == MemorySegment.NULL || arrLen <= 0 then Nil
      else
        val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
        (0 until arrLen).map { i =>
          val elem = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
          elem.get(ValueLayout.JAVA_LONG, Offset.IntValue)
        }.toList

  def decodeMap[K, V](
      seg: MemorySegment,
      keyCodec: Codec[K],
      valueCodec: Codec[V]
  ): Map[K, V] =
    val r = reinterpretResponse(seg)
    val rt = responseType(r)
    if rt == RespType.Null then Map.empty
    else
      val arrPtr = r.get(ValueLayout.ADDRESS, Offset.ArrayValue)
      val arrLen = r.get(ValueLayout.JAVA_LONG, Offset.ArrayValueLen).toInt
      if arrPtr == MemorySegment.NULL || arrLen <= 0 then Map.empty
      else
        val entries = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
        (0 until arrLen).map { i =>
          val entry = entries.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
          val keyPtr = entry.get(ValueLayout.ADDRESS, Offset.MapKey)
          val valPtr = entry.get(ValueLayout.ADDRESS, Offset.MapValue)
          val kSeg = keyPtr.reinterpret(RESPONSE_SIZE)
          val vSeg = valPtr.reinterpret(RESPONSE_SIZE)
          keyCodec.decode(decodeBytes(kSeg)) -> valueCodec.decode(decodeBytes(vSeg))
        }.toMap

  def decodeMapStringString(seg: MemorySegment): Map[String, String] =
    decodeMap(seg, Codec.utf8Codec, Codec.utf8Codec)

  def decodeUnit(seg: MemorySegment): Unit = ()

  def isNull(seg: MemorySegment): Boolean =
    seg == MemorySegment.NULL || seg.address() == 0L ||
      responseType(reinterpretResponse(seg)) == RespType.Null

