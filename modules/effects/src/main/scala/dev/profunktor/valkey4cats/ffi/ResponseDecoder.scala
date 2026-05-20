package dev.profunktor.valkey4cats.ffi

import dev.profunktor.valkey4cats.arguments.GeoPosition
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.results.*
import java.lang.foreign.{MemorySegment, ValueLayout}

private[valkey4cats] final class ResponseDecoder[K, V](
    keyCodec: Codec[K],
    valueCodec: Codec[V]
):
  import ResponseDecoder.*

  def decodeScorePairs(seg: MemorySegment): List[ScoredValue[V]] =
    val r = reinterpret(seg)
    val rt = respType(r)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen <= 0 then Nil
    else if rt == RT_MAP then
      mapEntries(arrPtr, arrLen) { (keyPtr, valPtr) =>
        val member = valueCodec.decode(ResponseParser.decodeBytes(reinterpret(keyPtr)))
        val score = ResponseParser.decodeDouble(reinterpret(valPtr))
        ScoredValue(member, score)
      }
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      val firstRt = respType(arr.asSlice(0L, RESPONSE_SIZE))
      if firstRt == RT_ARRAY then
        nestedPairs(arr, arrLen) { inner =>
          val member = valueCodec.decode(ResponseParser.decodeBytes(inner.asSlice(0L, RESPONSE_SIZE)))
          val score = ResponseParser.decodeDouble(inner.asSlice(RESPONSE_SIZE, RESPONSE_SIZE))
          ScoredValue(member, score)
        }
      else
        flatPairs(arr, arrLen) { (valElem, scoreElem) =>
          val v = valueCodec.decode(ResponseParser.decodeBytes(valElem))
          val score = ResponseParser.decodeDouble(scoreElem)
          ScoredValue(v, score)
        }

  def decodeKVPairs(seg: MemorySegment): List[(K, V)] =
    val r = reinterpret(seg)
    val rt = respType(r)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen <= 0 then Nil
    else if rt == RT_MAP then
      mapEntries(arrPtr, arrLen) { (keyPtr, valPtr) =>
        val k = keyCodec.decode(ResponseParser.decodeBytes(reinterpret(keyPtr)))
        val v = valueCodec.decode(ResponseParser.decodeBytes(reinterpret(valPtr)))
        (k, v)
      }
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      val firstRt = respType(arr.asSlice(0L, RESPONSE_SIZE))
      if firstRt == RT_ARRAY then
        nestedPairs(arr, arrLen) { inner =>
          val k = keyCodec.decode(ResponseParser.decodeBytes(inner.asSlice(0L, RESPONSE_SIZE)))
          val v = valueCodec.decode(ResponseParser.decodeBytes(inner.asSlice(RESPONSE_SIZE, RESPONSE_SIZE)))
          (k, v)
        }
      else
        flatPairs(arr, arrLen) { (keyElem, valElem) =>
          val k = keyCodec.decode(ResponseParser.decodeBytes(keyElem))
          val v = valueCodec.decode(ResponseParser.decodeBytes(valElem))
          (k, v)
        }

  def decodeKeyListPair(seg: MemorySegment): Option[(K, List[V])] =
    if ResponseParser.isNull(seg) then None
    else
      val r = reinterpret(seg)
      val rt = respType(r)
      if rt == RT_MAP then
        val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
        val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
        if arrPtr == MemorySegment.NULL || arrLen <= 0 then None
        else
          val entries = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
          val entry = entries.asSlice(0L, RESPONSE_SIZE)
          val keyPtr = entry.get(ValueLayout.ADDRESS, O_MAP_KEY)
          val valPtr = entry.get(ValueLayout.ADDRESS, O_MAP_VAL)
          val k = keyCodec.decode(ResponseParser.decodeBytes(reinterpret(keyPtr)))
          val values = ResponseParser.decodeList(reinterpret(valPtr), valueCodec)
          Some((k, values))
      else
        val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
        val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
        if arrPtr == MemorySegment.NULL || arrLen < 2 then None
        else
          val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
          val k = keyCodec.decode(ResponseParser.decodeBytes(arr.asSlice(0L, RESPONSE_SIZE)))
          val values = ResponseParser.decodeList(arr.asSlice(RESPONSE_SIZE, RESPONSE_SIZE), valueCodec)
          Some((k, values))

  def decodeKeyScoreListPair(seg: MemorySegment): Option[(K, List[ScoredValue[V]])] =
    if ResponseParser.isNull(seg) then None
    else
      val r = reinterpret(seg)
      val rt = respType(r)
      if rt == RT_MAP then
        val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
        val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
        if arrPtr == MemorySegment.NULL || arrLen <= 0 then None
        else
          val entries = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
          val entry = entries.asSlice(0L, RESPONSE_SIZE)
          val keyPtr = entry.get(ValueLayout.ADDRESS, O_MAP_KEY)
          val valPtr = entry.get(ValueLayout.ADDRESS, O_MAP_VAL)
          val k = keyCodec.decode(ResponseParser.decodeBytes(reinterpret(keyPtr)))
          val pairs = decodeScorePairs(reinterpret(valPtr))
          Some((k, pairs))
      else
        val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
        val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
        if arrPtr == MemorySegment.NULL || arrLen < 2 then None
        else
          val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
          val k = keyCodec.decode(ResponseParser.decodeBytes(arr.asSlice(0L, RESPONSE_SIZE)))
          val pairs = decodeScorePairs(arr.asSlice(RESPONSE_SIZE, RESPONSE_SIZE))
          Some((k, pairs))

  def decodeScanResult[A](seg: MemorySegment, codec: Codec[A]): ScanResult[List[A]] =
    val r = reinterpret(seg)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen < 2 then ScanResult("0", Nil)
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      val cursor = ResponseParser.decodeString(arr.asSlice(0L, RESPONSE_SIZE))
      val elements = ResponseParser.decodeList(arr.asSlice(RESPONSE_SIZE, RESPONSE_SIZE), codec)
      ScanResult(cursor, elements)

  def decodeScanKVResult(seg: MemorySegment): ScanResult[List[(K, V)]] =
    val r = reinterpret(seg)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen < 2 then ScanResult("0", Nil)
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      val cursor = ResponseParser.decodeString(arr.asSlice(0L, RESPONSE_SIZE))
      val kvList = decodeKVPairs(arr.asSlice(RESPONSE_SIZE, RESPONSE_SIZE))
      ScanResult(cursor, kvList)

  def decodeScanScoreResult(seg: MemorySegment): ScanResult[List[ScoredValue[V]]] =
    val r = reinterpret(seg)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen < 2 then ScanResult("0", Nil)
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      val cursor = ResponseParser.decodeString(arr.asSlice(0L, RESPONSE_SIZE))
      val pairs = decodeScorePairsFromSeg(arr.asSlice(RESPONSE_SIZE, RESPONSE_SIZE))
      ScanResult(cursor, pairs)

  def decodeStreamEntries(seg: MemorySegment): Map[String, List[(K, V)]] =
    val r = reinterpret(seg)
    val rt = respType(r)
    if rt == RT_NULL then Map.empty
    else if rt == RT_MAP then
      val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
      val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
      if arrPtr == MemorySegment.NULL || arrLen <= 0 then Map.empty
      else
        mapEntries(arrPtr, arrLen) { (keyPtr, valPtr) =>
          val id = ResponseParser.decodeString(reinterpret(keyPtr))
          val kvs = decodeKVPairs(reinterpret(valPtr))
          id -> kvs
        }.toMap
    else
      val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
      val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
      if arrPtr == MemorySegment.NULL || arrLen <= 0 then Map.empty
      else
        val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
        (0 until arrLen).map { i =>
          val entrySeg = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
          val innerPtr = entrySeg.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
          val innerLen = entrySeg.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
          if innerPtr == MemorySegment.NULL || innerLen < 2 then "" -> Nil
          else
            val inner = innerPtr.reinterpret(innerLen.toLong * RESPONSE_SIZE)
            val id = ResponseParser.decodeString(inner.asSlice(0L, RESPONSE_SIZE))
            val kvs = decodeKVPairs(inner.asSlice(RESPONSE_SIZE, RESPONSE_SIZE))
            id -> kvs
        }.toMap

  def decodeXReadResult(seg: MemorySegment): Option[Map[K, Map[String, List[(K, V)]]]] =
    if ResponseParser.isNull(seg) then None
    else
      val r = reinterpret(seg)
      val rt = respType(r)
      if rt == RT_NULL then None
      else if rt == RT_MAP then
        val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
        val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
        if arrPtr == MemorySegment.NULL || arrLen <= 0 then Some(Map.empty)
        else
          val result = mapEntries(arrPtr, arrLen) { (keyPtr, valPtr) =>
            val streamKey = keyCodec.decode(ResponseParser.decodeBytes(reinterpret(keyPtr)))
            val entries = decodeStreamEntries(reinterpret(valPtr))
            streamKey -> entries
          }.toMap
          Some(result)
      else
        val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
        val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
        if arrPtr == MemorySegment.NULL || arrLen <= 0 then Some(Map.empty)
        else
          val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
          val result = (0 until arrLen).flatMap { i =>
            val pairSeg = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
            val innerPtr = pairSeg.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
            val innerLen = pairSeg.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
            if innerPtr == MemorySegment.NULL || innerLen < 2 then None
            else
              val inner = innerPtr.reinterpret(innerLen.toLong * RESPONSE_SIZE)
              val streamKey = keyCodec.decode(ResponseParser.decodeBytes(inner.asSlice(0L, RESPONSE_SIZE)))
              val entries = decodeStreamEntries(inner.asSlice(RESPONSE_SIZE, RESPONSE_SIZE))
              Some(streamKey -> entries)
          }.toMap
          Some(result)

  def decodeAutoClaimResult(seg: MemorySegment): AutoClaimResult[K, V] =
    val r = reinterpret(seg)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen < 2 then AutoClaimResult("0-0", Map.empty, Nil)
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      val nextCursor = ResponseParser.decodeString(arr.asSlice(0L, RESPONSE_SIZE))
      val claimed = decodeStreamEntries(arr.asSlice(RESPONSE_SIZE, RESPONSE_SIZE))
      val deleted = if arrLen >= 3 then
        ResponseParser.decodeList(arr.asSlice(2L * RESPONSE_SIZE, RESPONSE_SIZE), Codec.utf8Codec)
      else Nil
      AutoClaimResult(nextCursor, claimed, deleted)

  def decodeConsumersPending(seg: MemorySegment): List[PendingSummary.ConsumerPending[K]] =
    val r = reinterpret(seg)
    val rt = respType(r)
    if rt == RT_NULL then Nil
    else
      val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
      val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
      if arrPtr == MemorySegment.NULL || arrLen <= 0 then Nil
      else
        val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
        (0 until arrLen).map { i =>
          val entry = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
          val entryRt = respType(entry)
          if entryRt == RT_MAP then
            val ePtr = entry.get(ValueLayout.ADDRESS, O_MAP_KEY)
            val vPtr = entry.get(ValueLayout.ADDRESS, O_MAP_VAL)
            val consumer = keyCodec.decode(ResponseParser.decodeBytes(reinterpret(ePtr)))
            val pending = ResponseParser.decodeString(reinterpret(vPtr)).toLong
            PendingSummary.ConsumerPending(consumer, pending)
          else
            val innerPtr = entry.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
            val innerLen = entry.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
            val inner = innerPtr.reinterpret(innerLen.toLong * RESPONSE_SIZE)
            val consumer = keyCodec.decode(ResponseParser.decodeBytes(inner.asSlice(0L, RESPONSE_SIZE)))
            val pending = ResponseParser.decodeString(inner.asSlice(RESPONSE_SIZE, RESPONSE_SIZE)).toLong
            PendingSummary.ConsumerPending(consumer, pending)
        }.toList

  def decodeLongList(seg: MemorySegment): List[Long] =
    ResponseParser.decodeListLong(seg)

  def decodeBooleanList(seg: MemorySegment): List[Boolean] =
    val r = reinterpret(seg)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen <= 0 then Nil
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      (0 until arrLen).map { i =>
        val elem = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
        ResponseParser.decodeBoolean(elem)
      }.toList

  def decodeOptionalDouble(seg: MemorySegment): Option[Double] =
    if ResponseParser.isNull(seg) then None
    else Some(ResponseParser.decodeDouble(seg))

  def decodeOptionalRankScore(seg: MemorySegment): Option[(Long, Double)] =
    if ResponseParser.isNull(seg) then None
    else
      val r = reinterpret(seg)
      val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
      val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
      if arrPtr == MemorySegment.NULL || arrLen < 2 then None
      else
        val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
        val rank = ResponseParser.decodeLong(arr.asSlice(0L, RESPONSE_SIZE))
        val score = ResponseParser.decodeDouble(arr.asSlice(RESPONSE_SIZE, RESPONSE_SIZE))
        Some((rank, score))

  def decodeOptionalKeyValue(seg: MemorySegment): Option[(K, V)] =
    if ResponseParser.isNull(seg) then None
    else
      val r = reinterpret(seg)
      val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
      val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
      if arrPtr == MemorySegment.NULL || arrLen < 2 then None
      else
        val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
        val k = keyCodec.decode(ResponseParser.decodeBytes(arr.asSlice(0L, RESPONSE_SIZE)))
        val v = valueCodec.decode(ResponseParser.decodeBytes(arr.asSlice(RESPONSE_SIZE, RESPONSE_SIZE)))
        Some((k, v))

  def decodeOptionalKeyValueScore(seg: MemorySegment): Option[(K, V, Double)] =
    if ResponseParser.isNull(seg) then None
    else
      val r = reinterpret(seg)
      val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
      val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
      if arrPtr == MemorySegment.NULL || arrLen < 3 then None
      else
        val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
        val k = keyCodec.decode(ResponseParser.decodeBytes(arr.asSlice(0L, RESPONSE_SIZE)))
        val v = valueCodec.decode(ResponseParser.decodeBytes(arr.asSlice(RESPONSE_SIZE, RESPONSE_SIZE)))
        val score = ResponseParser.decodeDouble(arr.asSlice(2L * RESPONSE_SIZE, RESPONSE_SIZE))
        Some((k, v, score))

  def decodeOptionalScoreList(seg: MemorySegment): List[Option[Double]] =
    val r = reinterpret(seg)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen <= 0 then Nil
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      (0 until arrLen).map { i =>
        val elem = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
        val rt = respType(elem)
        if rt == RT_NULL then None
        else Some(ResponseParser.decodeDouble(elem))
      }.toList

  def decodeMapKeyLong(seg: MemorySegment): Map[K, Long] =
    val r = reinterpret(seg)
    val rt = respType(r)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen <= 0 then Map.empty[K, Long]
    else if rt == RT_MAP then
      mapEntries(arrPtr, arrLen) { (keyPtr, valPtr) =>
        val k = keyCodec.decode(ResponseParser.decodeBytes(reinterpret(keyPtr)))
        val v = ResponseParser.decodeLong(reinterpret(valPtr))
        k -> v
      }.toMap
    else Map.empty[K, Long]

  def decodeGeoPositions(seg: MemorySegment): List[Option[GeoPosition]] =
    val r = reinterpret(seg)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen <= 0 then Nil
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      (0 until arrLen).map { i =>
        val elem = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
        if ResponseParser.isNull(elem) then None
        else
          val innerPtr = elem.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
          val innerLen = elem.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
          if innerPtr == MemorySegment.NULL || innerLen < 2 then None
          else
            val inner = innerPtr.reinterpret(innerLen.toLong * RESPONSE_SIZE)
            val lon = ResponseParser.decodeDouble(inner.asSlice(0L, RESPONSE_SIZE))
            val lat = ResponseParser.decodeDouble(inner.asSlice(RESPONSE_SIZE, RESPONSE_SIZE))
            Some(GeoPosition(lon, lat))
      }.toList

  def decodePendingSummary(seg: MemorySegment): PendingSummary[K] =
    val r = reinterpret(seg)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen < 4 then PendingSummary(0L, None, None, Nil)
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      val count = ResponseParser.decodeLong(arr.asSlice(0L, RESPONSE_SIZE))
      val smallest = ResponseParser.decodeOptional(arr.asSlice(RESPONSE_SIZE, RESPONSE_SIZE), Codec.utf8Codec)
      val greatest = ResponseParser.decodeOptional(arr.asSlice(2L * RESPONSE_SIZE, RESPONSE_SIZE), Codec.utf8Codec)
      val consumers = decodeConsumersPending(arr.asSlice(3L * RESPONSE_SIZE, RESPONSE_SIZE))
      PendingSummary(count, smallest, greatest, consumers)

  def decodePendingRange(seg: MemorySegment): List[PendingEntry[K]] =
    val r = reinterpret(seg)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen <= 0 then Nil
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      (0 until arrLen).map { i =>
        val entry = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
        val innerPtr = entry.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
        val innerLen = entry.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
        val inner = innerPtr.reinterpret(innerLen.toLong * RESPONSE_SIZE)
        val messageId = ResponseParser.decodeString(inner.asSlice(0L, RESPONSE_SIZE))
        val consumer = keyCodec.decode(ResponseParser.decodeBytes(inner.asSlice(RESPONSE_SIZE, RESPONSE_SIZE)))
        val idle = ResponseParser.decodeLong(inner.asSlice(2L * RESPONSE_SIZE, RESPONSE_SIZE))
        val deliveryCount = ResponseParser.decodeLong(inner.asSlice(3L * RESPONSE_SIZE, RESPONSE_SIZE))
        PendingEntry(messageId, consumer, idle, deliveryCount)
      }.toList

  def decodeAutoClaimIdResult(seg: MemorySegment): AutoClaimIdResult =
    val r = reinterpret(seg)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen < 2 then AutoClaimIdResult("0-0", Nil, Nil)
    else
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      val nextCursor = ResponseParser.decodeString(arr.asSlice(0L, RESPONSE_SIZE))
      val claimedIds = ResponseParser.decodeList(arr.asSlice(RESPONSE_SIZE, RESPONSE_SIZE), Codec.utf8Codec)
      val deletedIds = if arrLen >= 3 then ResponseParser.decodeList(arr.asSlice(2L * RESPONSE_SIZE, RESPONSE_SIZE), Codec.utf8Codec) else Nil
      AutoClaimIdResult(nextCursor, claimedIds, deletedIds)

  private def decodeScorePairsFromSeg(seg: MemorySegment): List[ScoredValue[V]] =
    val r = reinterpret(seg)
    val rt = respType(r)
    val arrPtr = r.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
    val arrLen = r.get(ValueLayout.JAVA_LONG, O_ARRAY_LEN).toInt
    if arrPtr == MemorySegment.NULL || arrLen <= 0 then Nil
    else if rt == RT_ARRAY then
      val arr = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
      flatPairs(arr, arrLen) { (memberElem, scoreElem) =>
        val member = valueCodec.decode(ResponseParser.decodeBytes(memberElem))
        val score = ResponseParser.decodeDouble(scoreElem)
        ScoredValue(member, score)
      }
    else Nil

private[valkey4cats] object ResponseDecoder:
  private val RESPONSE_SIZE = 96L

  private val O_ARRAY_PTR = 48L
  private val O_ARRAY_LEN = 56L
  private val O_MAP_KEY = 64L
  private val O_MAP_VAL = 72L

  private val RT_NULL = 0
  private val RT_ARRAY = 5
  private val RT_MAP = 6

  private def reinterpret(seg: MemorySegment): MemorySegment =
    seg.reinterpret(RESPONSE_SIZE)

  private def respType(seg: MemorySegment): Int =
    seg.get(ValueLayout.JAVA_INT, 0L)

  private inline def mapEntries[A](arrPtr: MemorySegment, arrLen: Int)(f: (MemorySegment, MemorySegment) => A): List[A] =
    val entries = arrPtr.reinterpret(arrLen.toLong * RESPONSE_SIZE)
    (0 until arrLen).map { i =>
      val entry = entries.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
      val keyPtr = entry.get(ValueLayout.ADDRESS, O_MAP_KEY)
      val valPtr = entry.get(ValueLayout.ADDRESS, O_MAP_VAL)
      f(keyPtr, valPtr)
    }.toList

  private inline def nestedPairs[A](arr: MemorySegment, arrLen: Int)(f: MemorySegment => A): List[A] =
    (0 until arrLen).map { i =>
      val pairSeg = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
      val innerPtr = pairSeg.get(ValueLayout.ADDRESS, O_ARRAY_PTR)
      val inner = innerPtr.reinterpret(2L * RESPONSE_SIZE)
      f(inner)
    }.toList

  private inline def flatPairs[A](arr: MemorySegment, arrLen: Int)(f: (MemorySegment, MemorySegment) => A): List[A] =
    (0 until arrLen by 2).map { i =>
      val a = arr.asSlice(i.toLong * RESPONSE_SIZE, RESPONSE_SIZE)
      val b = arr.asSlice((i + 1).toLong * RESPONSE_SIZE, RESPONSE_SIZE)
      f(a, b)
    }.toList
