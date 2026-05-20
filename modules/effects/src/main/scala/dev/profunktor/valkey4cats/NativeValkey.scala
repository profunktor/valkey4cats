package dev.profunktor.valkey4cats

import cats.effect.Async
import cats.syntax.all.*
import dev.profunktor.valkey4cats.arguments.*
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.ffi.{ArgEncoder as Enc, Cmd, CmdOrdinal, CommandType, Decode, ResponseDecoder, ResponseParser}
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.results.*
import java.lang.foreign.MemorySegment

@scala.annotation.nowarn("msg=unused implicit parameter")
private[valkey4cats] final class NativeValkey[F[_], K, V](
    client: NativeClient[F],
    keyCodec: Codec[K],
    valueCodec: Codec[V]
)(using F: Async[F], log: Log[F])
    extends BaseValkeyCommands[F, K, V](
      ffi.CommandDispatcher.native[F](client.handle, client.registry),
      keyCodec,
      valueCodec
    ):
  private[valkey4cats] def pendingCallbackCount: Int = client.registry.pendingCount

@scala.annotation.nowarn("msg=unused implicit parameter")
private[valkey4cats] abstract class BaseValkeyCommands[F[_], K, V](
    dispatcher: ffi.CommandDispatcher[F],
    keyCodec: Codec[K],
    valueCodec: Codec[V]
)(using F: Async[F], log: Log[F])
    extends ValkeyCommands[F, K, V]:

  private val dec = new ResponseDecoder[K, V](keyCodec, valueCodec)

  private def encKey(key: K): Array[Byte] = keyCodec.encode(key)
  private def encVal(value: V): Array[Byte] = valueCodec.encode(value)

  private def customExec[A](cmdName: String, args: Array[Array[Byte]])(
      decode: MemorySegment => A
  ): F[ValkeyResponse[A]] =
    exec(Cmd(CommandType.CustomCommand, decode), Array(Enc.strBytes(cmdName)) ++ args)

  protected def exec[A](cmd: Cmd[A], args: Array[Array[Byte]]): F[ValkeyResponse[A]] =
    dispatcher.exec(cmd, args)

  // ─── StringCommands ───

  override def get(key: K): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.Get, Decode.optional(valueCodec)), Array(encKey(key)))

  override def set(key: K, value: V): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.Set, Decode.unit), Array(encKey(key), encVal(value)))

  override def set(key: K, value: V, options: SetOptions): F[ValkeyResponse[SetResult[V]]] =
    val baseArgs = Array(encKey(key), encVal(value))
    val condArgs = options.condition.fold(Array.empty[Array[Byte]])(Enc.setCondition)
    val expiryArgs = options.expiry.fold(Array.empty[Array[Byte]])(Enc.setExpiry)
    val getArg: Array[Array[Byte]] = if options.returnOldValue then Array(Enc.GET) else Array.empty
    val allArgs = baseArgs ++ condArgs ++ expiryArgs ++ getArg
    if options.returnOldValue then
      exec(Cmd(CommandType.Set, { seg =>
        if seg == MemorySegment.NULL || seg.address() == 0L then SetResult.Written
        else SetResult.Replaced(valueCodec.decode(ResponseParser.decodeBytes(seg)))
      }), allArgs)
    else
      exec(Cmd(CommandType.Set, { seg =>
        if seg == MemorySegment.NULL || seg.address() == 0L then SetResult.NotSet
        else SetResult.Written
      }), allArgs)

  override def mGet(keys: Set[K]): F[ValkeyResponse[Map[K, V]]] =
    val keyList = keys.toList
    exec(Cmd(CommandType.MGet, { seg =>
      val values = ResponseParser.decodeOptionalList(seg, valueCodec)
      keyList.zip(values).collect { case (k, Some(v)) => k -> v }.toMap
    }), keyList.map(encKey).toArray)

  override def mSet(keyValues: Map[K, V]): F[ValkeyResponse[Unit]] =
    val args = keyValues.flatMap((k, v) => Seq(encKey(k), encVal(v))).toArray
    exec(Cmd(CommandType.MSet, Decode.unit), args)

  override def incr(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.Incr, Decode.long), Array(encKey(key)))

  override def incrBy(key: K, amount: Long): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.IncrBy, Decode.long), Array(encKey(key), Enc.longBytes(amount)))

  override def decr(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.Decr, Decode.long), Array(encKey(key)))

  override def decrBy(key: K, amount: Long): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.DecrBy, Decode.long), Array(encKey(key), Enc.longBytes(amount)))

  override def append(key: K, value: V): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.Append, Decode.long), Array(encKey(key), encVal(value)))

  override def strlen(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.Strlen, Decode.long), Array(encKey(key)))

  override def getEx(key: K): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.GetEx, Decode.optional(valueCodec)), Array(encKey(key)))

  override def getEx(key: K, expiry: GetExExpiry): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.GetEx, Decode.optional(valueCodec)), Array(encKey(key)) ++ Enc.getExExpiry(expiry))

  override def getDel(key: K): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.GetDel, Decode.optional(valueCodec)), Array(encKey(key)))

  override def incrByFloat(key: K, amount: Double): F[ValkeyResponse[Double]] =
    exec(Cmd(CommandType.IncrByFloat, Decode.double), Array(encKey(key), Enc.doubleBytes(amount)))

  override def setNx(key: K, value: V): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.Set, seg => !ResponseParser.isNull(seg)), Array(encKey(key), encVal(value), Enc.NX))

  override def mSetNx(keyValues: Map[K, V]): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.MSetNx, Decode.boolean), keyValues.flatMap((k, v) => Seq(encKey(k), encVal(v))).toArray)

  override def getRange(key: K, start: Long, end: Long): F[ValkeyResponse[V]] =
    exec(Cmd(CommandType.GetRange, Decode.value(valueCodec)), Array(encKey(key), Enc.longBytes(start), Enc.longBytes(end)))

  override def setRange(key: K, offset: Long, value: V): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.SetRange, Decode.long), Array(encKey(key), Enc.longBytes(offset), encVal(value)))

  override def lcs(key1: K, key2: K): F[ValkeyResponse[V]] =
    exec(Cmd(CommandType.Lcs, Decode.value(valueCodec)), Array(encKey(key1), encKey(key2)))

  override def lcsLen(key1: K, key2: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.Lcs, Decode.long), Array(encKey(key1), encKey(key2), Enc.LEN))

  // ─── KeyCommands ───

  override def del(keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.Del, Decode.long), keys.map(encKey).toArray)

  override def exists(key: K): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.Exists, Decode.boolean), Array(encKey(key)))

  override def existsMany(keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.Exists, Decode.long), keys.map(encKey).toArray)

  override def unlink(keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.Unlink, Decode.long), keys.map(encKey).toArray)

  override def expire(key: K, seconds: Long): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.Expire, Decode.boolean), Array(encKey(key), Enc.longBytes(seconds)))

  override def expire(key: K, seconds: Long, condition: ExpireCondition): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.Expire, Decode.boolean), Array(encKey(key), Enc.longBytes(seconds), Enc.expireCondition(condition)))

  override def pexpire(key: K, milliseconds: Long): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.PExpire, Decode.boolean), Array(encKey(key), Enc.longBytes(milliseconds)))

  override def pexpire(key: K, milliseconds: Long, condition: ExpireCondition): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.PExpire, Decode.boolean), Array(encKey(key), Enc.longBytes(milliseconds), Enc.expireCondition(condition)))

  override def expireAt(key: K, unixSeconds: Long): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.ExpireAt, Decode.boolean), Array(encKey(key), Enc.longBytes(unixSeconds)))

  override def expireAt(key: K, unixSeconds: Long, condition: ExpireCondition): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.ExpireAt, Decode.boolean), Array(encKey(key), Enc.longBytes(unixSeconds), Enc.expireCondition(condition)))

  override def pexpireAt(key: K, unixMilliseconds: Long): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.PExpireAt, Decode.boolean), Array(encKey(key), Enc.longBytes(unixMilliseconds)))

  override def pexpireAt(key: K, unixMilliseconds: Long, condition: ExpireCondition): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.PExpireAt, Decode.boolean), Array(encKey(key), Enc.longBytes(unixMilliseconds), Enc.expireCondition(condition)))

  override def ttl(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.TTL, Decode.long), Array(encKey(key)))

  override def pttl(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.PTTL, Decode.long), Array(encKey(key)))

  override def expireTime(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ExpireTime, Decode.long), Array(encKey(key)))

  override def pexpireTime(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.PExpireTime, Decode.long), Array(encKey(key)))

  override def persist(key: K): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.Persist, Decode.boolean), Array(encKey(key)))

  override def rename(key: K, newKey: K): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.Rename, Decode.unit), Array(encKey(key), encKey(newKey)))

  override def renameNx(key: K, newKey: K): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.RenameNx, Decode.boolean), Array(encKey(key), encKey(newKey)))

  override def typeOf(key: K): F[ValkeyResponse[String]] =
    exec(Cmd(CommandType.Type, Decode.string), Array(encKey(key)))

  override def objectEncoding(key: K): F[ValkeyResponse[Option[String]]] =
    exec(Cmd(CommandType.ObjectEncoding, seg => Option(ResponseParser.decodeString(seg)).filter(_.nonEmpty)), Array(encKey(key)))

  override def touch(keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.Touch, Decode.long), keys.map(encKey).toArray)

  override def copy(source: K, destination: K): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.Copy, Decode.boolean), Array(encKey(source), encKey(destination)))

  override def randomKey: F[ValkeyResponse[Option[K]]] =
    exec(Cmd(CommandType.RandomKey, Decode.optional(keyCodec)), Array.empty)

  override def objectFreq(key: K): F[ValkeyResponse[Option[Long]]] =
    exec(Cmd(CommandType.ObjectFreq, Decode.optionalLong), Array(encKey(key)))

  override def objectIdletime(key: K): F[ValkeyResponse[Option[Long]]] =
    exec(Cmd(CommandType.ObjectIdletime, Decode.optionalLong), Array(encKey(key)))

  override def objectRefcount(key: K): F[ValkeyResponse[Option[Long]]] =
    exec(Cmd(CommandType.ObjectRefcount, Decode.optionalLong), Array(encKey(key)))

  override def sort(key: K): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.Sort, Decode.list(valueCodec)), Array(encKey(key)))

  override def sortStore(key: K, destination: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.Sort, Decode.long), Array(encKey(key), Enc.STORE, encKey(destination)))

  override def sortReadOnly(key: K): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.SortReadOnly, Decode.list(valueCodec)), Array(encKey(key)))

  override def dump(key: K): F[ValkeyResponse[Option[Array[Byte]]]] =
    exec(Cmd(CommandType.Dump, { seg =>
      if ResponseParser.isNull(seg) then None
      else
        val bytes = ResponseParser.decodeBytes(seg)
        if bytes.isEmpty then None else Some(bytes)
    }), Array(encKey(key)))

  override def restore(key: K, ttlMillis: Long, serializedValue: Array[Byte]): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.Restore, Decode.unit), Array(encKey(key), Enc.longBytes(ttlMillis), serializedValue))

  override def waitReplicas(numReplicas: Long, timeout: Long): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.Wait, Decode.long), Array(Enc.longBytes(numReplicas), Enc.longBytes(timeout)))

  override def move(key: K, db: Long): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.Move, Decode.boolean), Array(encKey(key), Enc.longBytes(db)))

  override def scan(cursor: String): F[ValkeyResponse[ScanResult[List[K]]]] =
    exec(Cmd(CommandType.Scan, dec.decodeScanResult(_, keyCodec)), Array(Enc.strBytes(cursor)))

  override def scan(cursor: String, matchPattern: String, count: Long): F[ValkeyResponse[ScanResult[List[K]]]] =
    exec(Cmd(CommandType.Scan, dec.decodeScanResult(_, keyCodec)), Array(Enc.strBytes(cursor), Enc.MATCH, Enc.strBytes(matchPattern), Enc.COUNT, Enc.longBytes(count)))

  override def clusterScan(cursor: ClusterScanCursor): F[ValkeyResponse[ClusterScanResult[List[K]]]] =
    F.raiseError(new UnsupportedOperationException("clusterScan is not yet supported by the native FFI backend"))

  override def clusterScan(cursor: ClusterScanCursor, matchPattern: String, count: Long): F[ValkeyResponse[ClusterScanResult[List[K]]]] =
    F.raiseError(new UnsupportedOperationException("clusterScan is not yet supported by the native FFI backend"))

  // ─── HashCommands ───

  override def hset(key: K, fieldValues: Map[K, V]): F[ValkeyResponse[Long]] =
    val args = Array(encKey(key)) ++ fieldValues.flatMap((k, v) => Seq(encKey(k), encVal(v)))
    exec(Cmd(CommandType.HSet, Decode.long), args)

  override def hget(key: K, field: K): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.HGet, Decode.optional(valueCodec)), Array(encKey(key), encKey(field)))

  override def hgetall(key: K): F[ValkeyResponse[Map[K, V]]] =
    exec(Cmd(CommandType.HGetAll, Decode.map(keyCodec, valueCodec)), Array(encKey(key)))

  override def hmget(key: K, fields: K*): F[ValkeyResponse[List[Option[V]]]] =
    exec(Cmd(CommandType.HMGet, Decode.optionalList(valueCodec)), Array(encKey(key)) ++ fields.map(encKey))

  override def hdel(key: K, fields: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.HDel, Decode.long), Array(encKey(key)) ++ fields.map(encKey))

  override def hexists(key: K, field: K): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.HExists, Decode.boolean), Array(encKey(key), encKey(field)))

  override def hkeys(key: K): F[ValkeyResponse[List[K]]] =
    exec(Cmd(CommandType.HKeys, Decode.list(keyCodec)), Array(encKey(key)))

  override def hvals(key: K): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.HVals, Decode.list(valueCodec)), Array(encKey(key)))

  override def hlen(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.HLen, Decode.long), Array(encKey(key)))

  override def hincrBy(key: K, field: K, increment: Long): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.HIncrBy, Decode.long), Array(encKey(key), encKey(field), Enc.longBytes(increment)))

  override def hincrByFloat(key: K, field: K, increment: Double): F[ValkeyResponse[Double]] =
    exec(Cmd(CommandType.HIncrByFloat, Decode.double), Array(encKey(key), encKey(field), Enc.doubleBytes(increment)))

  override def hsetnx(key: K, field: K, value: V): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.HSetNx, Decode.boolean), Array(encKey(key), encKey(field), encVal(value)))

  override def hstrlen(key: K, field: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.HStrLen, Decode.long), Array(encKey(key), encKey(field)))

  override def hrandfield(key: K): F[ValkeyResponse[Option[K]]] =
    exec(Cmd(CommandType.HRandField, Decode.optional(keyCodec)), Array(encKey(key)))

  override def hrandfieldWithCount(key: K, count: Long): F[ValkeyResponse[List[K]]] =
    exec(Cmd(CommandType.HRandField, Decode.list(keyCodec)), Array(encKey(key), Enc.longBytes(count)))

  override def hrandfieldWithCountWithValues(key: K, count: Long): F[ValkeyResponse[List[(K, V)]]] =
    exec(Cmd(CommandType.HRandField, dec.decodeKVPairs), Array(encKey(key), Enc.longBytes(count), Enc.WITHVALUES))

  override def hscan(key: K, cursor: String): F[ValkeyResponse[ScanResult[List[(K, V)]]]] =
    exec(Cmd(CommandType.HScan, dec.decodeScanKVResult), Array(encKey(key), Enc.strBytes(cursor)))

  override def hexpire(key: K, seconds: Long, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.longBytes(seconds), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HEXPIRE", args)(dec.decodeLongList)

  override def hexpire(key: K, seconds: Long, condition: ExpireCondition, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.longBytes(seconds), Enc.expireCondition(condition), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HEXPIRE", args)(dec.decodeLongList)

  override def hpexpire(key: K, milliseconds: Long, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.longBytes(milliseconds), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HPEXPIRE", args)(dec.decodeLongList)

  override def hpexpire(key: K, milliseconds: Long, condition: ExpireCondition, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.longBytes(milliseconds), Enc.expireCondition(condition), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HPEXPIRE", args)(dec.decodeLongList)

  override def hexpireAt(key: K, unixSeconds: Long, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.longBytes(unixSeconds), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HEXPIREAT", args)(dec.decodeLongList)

  override def hexpireAt(key: K, unixSeconds: Long, condition: ExpireCondition, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.longBytes(unixSeconds), Enc.expireCondition(condition), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HEXPIREAT", args)(dec.decodeLongList)

  override def hpexpireAt(key: K, unixMilliseconds: Long, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.longBytes(unixMilliseconds), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HPEXPIREAT", args)(dec.decodeLongList)

  override def hpexpireAt(key: K, unixMilliseconds: Long, condition: ExpireCondition, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.longBytes(unixMilliseconds), Enc.expireCondition(condition), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HPEXPIREAT", args)(dec.decodeLongList)

  override def httl(key: K, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HTTL", args)(dec.decodeLongList)

  override def hpttl(key: K, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HPTTL", args)(dec.decodeLongList)

  override def hexpireTime(key: K, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HEXPIRETIME", args)(dec.decodeLongList)

  override def hpexpireTime(key: K, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HPEXPIRETIME", args)(dec.decodeLongList)

  override def hpersist(key: K, fields: K*): F[ValkeyResponse[List[Long]]] =
    val args = Array(encKey(key), Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HPERSIST", args)(dec.decodeLongList)

  override def hgetex(key: K, expiry: HGetExExpiry, fields: K*): F[ValkeyResponse[List[Option[V]]]] =
    val args = Array(encKey(key)) ++ Enc.hGetExExpiry(expiry) ++ Array(Enc.FIELDS, Enc.intBytes(fields.length)) ++ fields.map(encKey)
    customExec("HGETEX", args)(Decode.optionalList(valueCodec))

  override def hsetex(key: K, fieldValues: Map[K, V], expiry: ExpirySet): F[ValkeyResponse[Long]] =
    if fieldValues.isEmpty then ValkeyResponse.ok(0L).pure[F]
    else
      val fvArgs = fieldValues.flatMap((k, v) => Seq(encKey(k), encVal(v))).toArray
      val args = Array(encKey(key)) ++ Enc.expirySet(expiry) ++ Array(Enc.FIELDS, Enc.intBytes(fieldValues.size)) ++ fvArgs
      customExec("HSETEX", args)(ResponseParser.decodeLong)

  override def hsetex(key: K, fieldValues: Map[K, V], expiry: ExpirySet, condition: FieldCondition): F[ValkeyResponse[Long]] =
    if fieldValues.isEmpty then ValkeyResponse.ok(0L).pure[F]
    else
      val fvArgs = fieldValues.flatMap((k, v) => Seq(encKey(k), encVal(v))).toArray
      val args = Array(encKey(key)) ++ Enc.expirySet(expiry) ++ Array(Enc.fieldCondition(condition), Enc.FIELDS, Enc.intBytes(fieldValues.size)) ++ fvArgs
      customExec("HSETEX", args)(ResponseParser.decodeLong)

  // ─── ListCommands ───

  override def lpush(key: K, elements: V*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.LPush, Decode.long), Array(encKey(key)) ++ elements.map(encVal))

  override def rpush(key: K, elements: V*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.RPush, Decode.long), Array(encKey(key)) ++ elements.map(encVal))

  override def lpop(key: K): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.LPop, Decode.optional(valueCodec)), Array(encKey(key)))

  override def rpop(key: K): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.RPop, Decode.optional(valueCodec)), Array(encKey(key)))

  override def lpopCount(key: K, count: Long): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.LPop, Decode.list(valueCodec)), Array(encKey(key), Enc.longBytes(count)))

  override def rpopCount(key: K, count: Long): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.RPop, Decode.list(valueCodec)), Array(encKey(key), Enc.longBytes(count)))

  override def lrange(key: K, start: Long, stop: Long): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.LRange, Decode.list(valueCodec)), Array(encKey(key), Enc.longBytes(start), Enc.longBytes(stop)))

  override def lindex(key: K, index: Long): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.LIndex, Decode.optional(valueCodec)), Array(encKey(key), Enc.longBytes(index)))

  override def llen(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.LLen, Decode.long), Array(encKey(key)))

  override def ltrim(key: K, start: Long, stop: Long): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.LTrim, Decode.unit), Array(encKey(key), Enc.longBytes(start), Enc.longBytes(stop)))

  override def lset(key: K, index: Long, element: V): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.LSet, Decode.unit), Array(encKey(key), Enc.longBytes(index), encVal(element)))

  override def lrem(key: K, count: Long, element: V): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.LRem, Decode.long), Array(encKey(key), Enc.longBytes(count), encVal(element)))

  override def linsert(key: K, position: InsertPosition, pivot: V, element: V): F[ValkeyResponse[InsertResult]] =
    val posArg = position match
      case InsertPosition.Before => Enc.BEFORE
      case InsertPosition.After  => Enc.AFTER
    exec(Cmd(CommandType.LInsert, { seg =>
      val result = ResponseParser.decodeLong(seg)
      if result == -1L then InsertResult.PivotNotFound
      else InsertResult.Inserted(result)
    }), Array(encKey(key), posArg, encVal(pivot), encVal(element)))

  override def lpos(key: K, element: V): F[ValkeyResponse[Option[Long]]] =
    exec(Cmd(CommandType.LPos, Decode.optionalLong), Array(encKey(key), encVal(element)))

  override def lpushx(key: K, elements: V*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.LPushX, Decode.long), Array(encKey(key)) ++ elements.map(encVal))

  override def rpushx(key: K, elements: V*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.RPushX, Decode.long), Array(encKey(key)) ++ elements.map(encVal))

  override def lmove(source: K, destination: K, from: ListDirection, to: ListDirection): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.LMove, Decode.optional(valueCodec)), Array(encKey(source), encKey(destination), Enc.direction(from), Enc.direction(to)))

  override def blpop(keys: List[K], timeout: Double): F[ValkeyResponse[Option[(K, V)]]] =
    exec(Cmd(CommandType.BLPop, dec.decodeOptionalKeyValue), keys.map(encKey).toArray :+ Enc.doubleBytes(timeout))

  override def brpop(keys: List[K], timeout: Double): F[ValkeyResponse[Option[(K, V)]]] =
    exec(Cmd(CommandType.BRPop, dec.decodeOptionalKeyValue), keys.map(encKey).toArray :+ Enc.doubleBytes(timeout))

  override def lposCount(key: K, element: V, count: Long): F[ValkeyResponse[List[Long]]] =
    exec(Cmd(CommandType.LPos, dec.decodeLongList), Array(encKey(key), encVal(element), Enc.COUNT, Enc.longBytes(count)))

  override def blmove(source: K, destination: K, from: ListDirection, to: ListDirection, timeout: Double): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.BLMove, Decode.optional(valueCodec)), Array(encKey(source), encKey(destination), Enc.direction(from), Enc.direction(to), Enc.doubleBytes(timeout)))

  override def lmpop(keys: List[K], direction: ListDirection): F[ValkeyResponse[Option[(K, List[V])]]] =
    val args = Array(Enc.intBytes(keys.length)) ++ keys.map(encKey).toArray ++ Array(Enc.direction(direction))
    exec(Cmd(CommandType.LMPop, dec.decodeKeyListPair), args)

  override def lmpop(keys: List[K], direction: ListDirection, count: Long): F[ValkeyResponse[Option[(K, List[V])]]] =
    val args = Array(Enc.intBytes(keys.length)) ++ keys.map(encKey).toArray ++ Array(Enc.direction(direction), Enc.COUNT, Enc.longBytes(count))
    exec(Cmd(CommandType.LMPop, dec.decodeKeyListPair), args)

  override def blmpop(keys: List[K], direction: ListDirection, timeout: Double): F[ValkeyResponse[Option[(K, List[V])]]] =
    val args = Array(Enc.doubleBytes(timeout), Enc.intBytes(keys.length)) ++ keys.map(encKey).toArray ++ Array(Enc.direction(direction))
    exec(Cmd(CommandType.BLMPop, dec.decodeKeyListPair), args)

  override def blmpop(keys: List[K], direction: ListDirection, count: Long, timeout: Double): F[ValkeyResponse[Option[(K, List[V])]]] =
    val args = Array(Enc.doubleBytes(timeout), Enc.intBytes(keys.length)) ++ keys.map(encKey).toArray ++ Array(Enc.direction(direction), Enc.COUNT, Enc.longBytes(count))
    exec(Cmd(CommandType.BLMPop, dec.decodeKeyListPair), args)

  // ─── SetCommands ───

  override def sadd(key: K, members: V*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.SAdd, Decode.long), Array(encKey(key)) ++ members.map(encVal))

  override def srem(key: K, members: V*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.SRem, Decode.long), Array(encKey(key)) ++ members.map(encVal))

  override def smembers(key: K): F[ValkeyResponse[Set[V]]] =
    exec(Cmd(CommandType.SMembers, Decode.set(valueCodec)), Array(encKey(key)))

  override def sismember(key: K, member: V): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.SIsMember, Decode.boolean), Array(encKey(key), encVal(member)))

  override def smismember(key: K, members: V*): F[ValkeyResponse[List[Boolean]]] =
    exec(Cmd(CommandType.SMIsMember, dec.decodeBooleanList), Array(encKey(key)) ++ members.map(encVal))

  override def scard(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.SCard, Decode.long), Array(encKey(key)))

  override def sunion(keys: K*): F[ValkeyResponse[Set[V]]] =
    exec(Cmd(CommandType.SUnion, Decode.set(valueCodec)), keys.map(encKey).toArray)

  override def sunionstore(destination: K, keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.SUnionStore, Decode.long), Array(encKey(destination)) ++ keys.map(encKey))

  override def sinter(keys: K*): F[ValkeyResponse[Set[V]]] =
    exec(Cmd(CommandType.SInter, Decode.set(valueCodec)), keys.map(encKey).toArray)

  override def sinterstore(destination: K, keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.SInterStore, Decode.long), Array(encKey(destination)) ++ keys.map(encKey))

  override def sdiff(keys: K*): F[ValkeyResponse[Set[V]]] =
    exec(Cmd(CommandType.SDiff, Decode.set(valueCodec)), keys.map(encKey).toArray)

  override def sdiffstore(destination: K, keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.SDiffStore, Decode.long), Array(encKey(destination)) ++ keys.map(encKey))

  override def spop(key: K): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.SPop, Decode.optional(valueCodec)), Array(encKey(key)))

  override def spopCount(key: K, count: Long): F[ValkeyResponse[Set[V]]] =
    exec(Cmd(CommandType.SPop, Decode.set(valueCodec)), Array(encKey(key), Enc.longBytes(count)))

  override def srandmember(key: K): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.SRandMember, Decode.optional(valueCodec)), Array(encKey(key)))

  override def srandmemberCount(key: K, count: Long): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.SRandMember, Decode.list(valueCodec)), Array(encKey(key), Enc.longBytes(count)))

  override def smove(source: K, destination: K, member: V): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.SMove, Decode.boolean), Array(encKey(source), encKey(destination), encVal(member)))

  override def sintercard(keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.SInterCard, Decode.long), Array(Enc.intBytes(keys.length)) ++ keys.map(encKey))

  override def sintercard(limit: Long, keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.SInterCard, Decode.long), Array(Enc.intBytes(keys.length)) ++ keys.map(encKey) ++ Array(Enc.LIMIT, Enc.longBytes(limit)))

  override def sscan(key: K, cursor: String): F[ValkeyResponse[ScanResult[Set[V]]]] =
    exec(Cmd(CommandType.SScan, { seg =>
      val sr = dec.decodeScanResult(seg, valueCodec)
      ScanResult(sr.cursor, sr.values.toSet)
    }), Array(encKey(key), Enc.strBytes(cursor)))

  // ─── SortedSetCommands ───

  override def zadd(key: K, membersScores: Map[V, Double]): F[ValkeyResponse[Long]] =
    val scoreMembers = membersScores.flatMap((member, score) => Seq(Enc.doubleBytes(score), encVal(member))).toArray
    exec(Cmd(CommandType.ZAdd, Decode.long), Array(encKey(key)) ++ scoreMembers)

  override def zadd(key: K, membersScores: Map[V, Double], options: ZAddOptions): F[ValkeyResponse[Long]] =
    val condArgs = options.conditionalChange.fold(Array.empty[Array[Byte]])(Enc.zAddConditionalChange)
    val updateArgs = options.updateOption.fold(Array.empty[Array[Byte]])(Enc.zAddUpdateOption)
    val scoreMembers = membersScores.flatMap((member, score) => Seq(Enc.doubleBytes(score), encVal(member))).toArray
    exec(Cmd(CommandType.ZAdd, Decode.long), Array(encKey(key)) ++ condArgs ++ updateArgs ++ scoreMembers)

  override def zaddIncr(key: K, member: V, score: Double): F[ValkeyResponse[Option[Double]]] =
    exec(Cmd(CommandType.ZAdd, dec.decodeOptionalDouble), Array(encKey(key), Enc.INCR, Enc.doubleBytes(score), encVal(member)))

  override def zrem(key: K, members: V*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZRem, Decode.long), Array(encKey(key)) ++ members.map(encVal))

  override def zrange(key: K, start: Long, stop: Long): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.ZRange, Decode.list(valueCodec)), Array(encKey(key), Enc.longBytes(start), Enc.longBytes(stop)))

  override def zrangeWithScores(key: K, start: Long, stop: Long): F[ValkeyResponse[List[ScoredValue[V]]]] =
    exec(Cmd(CommandType.ZRange, dec.decodeScorePairs), Array(encKey(key), Enc.longBytes(start), Enc.longBytes(stop), Enc.WITHSCORES))

  override def zscore(key: K, member: V): F[ValkeyResponse[Option[Double]]] =
    exec(Cmd(CommandType.ZScore, dec.decodeOptionalDouble), Array(encKey(key), encVal(member)))

  override def zmscore(key: K, members: V*): F[ValkeyResponse[List[Option[Double]]]] =
    exec(Cmd(CommandType.ZMScore, dec.decodeOptionalScoreList), Array(encKey(key)) ++ members.map(encVal))

  override def zcard(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZCard, Decode.long), Array(encKey(key)))

  override def zrank(key: K, member: V): F[ValkeyResponse[Option[Long]]] =
    exec(Cmd(CommandType.ZRank, Decode.optionalLong), Array(encKey(key), encVal(member)))

  override def zrevrank(key: K, member: V): F[ValkeyResponse[Option[Long]]] =
    exec(Cmd(CommandType.ZRevRank, Decode.optionalLong), Array(encKey(key), encVal(member)))

  override def zincrby(key: K, increment: Double, member: V): F[ValkeyResponse[Double]] =
    exec(Cmd(CommandType.ZIncrBy, Decode.double), Array(encKey(key), Enc.doubleBytes(increment), encVal(member)))

  override def zcount(key: K, min: Double, max: Double): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZCount, Decode.long), Array(encKey(key), Enc.doubleBytes(min), Enc.doubleBytes(max)))

  override def zpopmin(key: K): F[ValkeyResponse[Option[ScoredValue[V]]]] =
    exec(Cmd(CommandType.ZPopMin, seg => dec.decodeScorePairs(seg).headOption), Array(encKey(key)))

  override def zpopminCount(key: K, count: Long): F[ValkeyResponse[List[ScoredValue[V]]]] =
    exec(Cmd(CommandType.ZPopMin, dec.decodeScorePairs), Array(encKey(key), Enc.longBytes(count)))

  override def zpopmax(key: K): F[ValkeyResponse[Option[ScoredValue[V]]]] =
    exec(Cmd(CommandType.ZPopMax, seg => dec.decodeScorePairs(seg).headOption), Array(encKey(key)))

  override def zpopmaxCount(key: K, count: Long): F[ValkeyResponse[List[ScoredValue[V]]]] =
    exec(Cmd(CommandType.ZPopMax, dec.decodeScorePairs), Array(encKey(key), Enc.longBytes(count)))

  override def zrandmember(key: K): F[ValkeyResponse[Option[V]]] =
    exec(Cmd(CommandType.ZRandMember, Decode.optional(valueCodec)), Array(encKey(key)))

  override def zrandmemberCount(key: K, count: Long): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.ZRandMember, Decode.list(valueCodec)), Array(encKey(key), Enc.longBytes(count)))

  override def zrandmemberWithScores(key: K, count: Long): F[ValkeyResponse[List[ScoredValue[V]]]] =
    exec(Cmd(CommandType.ZRandMember, dec.decodeScorePairs), Array(encKey(key), Enc.longBytes(count), Enc.WITHSCORES))

  override def zremrangebyrank(key: K, start: Long, stop: Long): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZRemRangeByRank, Decode.long), Array(encKey(key), Enc.longBytes(start), Enc.longBytes(stop)))

  override def zremrangebyscore(key: K, min: ScoreBoundary, max: ScoreBoundary): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZRemRangeByScore, Decode.long), Array(encKey(key), Enc.scoreBoundary(min), Enc.scoreBoundary(max)))

  override def zdiff(keys: K*): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.ZDiff, Decode.list(valueCodec)), Array(Enc.intBytes(keys.length)) ++ keys.map(encKey))

  override def zdiffstore(destination: K, keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZDiffStore, Decode.long), Array(encKey(destination), Enc.intBytes(keys.length)) ++ keys.map(encKey))

  override def zunion(keys: K*): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.ZUnion, Decode.list(valueCodec)), Array(Enc.intBytes(keys.length)) ++ keys.map(encKey))

  override def zunionstore(destination: K, keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZUnionStore, Decode.long), Array(encKey(destination), Enc.intBytes(keys.length)) ++ keys.map(encKey))

  override def zinter(keys: K*): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.ZInter, Decode.list(valueCodec)), Array(Enc.intBytes(keys.length)) ++ keys.map(encKey))

  override def zinterstore(destination: K, keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZInterStore, Decode.long), Array(encKey(destination), Enc.intBytes(keys.length)) ++ keys.map(encKey))

  override def zintercard(keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZInterCard, Decode.long), Array(Enc.intBytes(keys.length)) ++ keys.map(encKey))

  override def zintercard(limit: Long, keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZInterCard, Decode.long), Array(Enc.intBytes(keys.length)) ++ keys.map(encKey) ++ Array(Enc.LIMIT, Enc.longBytes(limit)))

  override def zrankWithScore(key: K, member: V): F[ValkeyResponse[Option[(Long, Double)]]] =
    exec(Cmd(CommandType.ZRank, dec.decodeOptionalRankScore), Array(encKey(key), encVal(member), Enc.WITHSCORE))

  override def zrevrankWithScore(key: K, member: V): F[ValkeyResponse[Option[(Long, Double)]]] =
    exec(Cmd(CommandType.ZRevRank, dec.decodeOptionalRankScore), Array(encKey(key), encVal(member), Enc.WITHSCORE))

  override def bzpopmin(keys: List[K], timeout: Double): F[ValkeyResponse[Option[(K, V, Double)]]] =
    exec(Cmd(CommandType.BZPopMin, dec.decodeOptionalKeyValueScore), keys.map(encKey).toArray :+ Enc.doubleBytes(timeout))

  override def bzpopmax(keys: List[K], timeout: Double): F[ValkeyResponse[Option[(K, V, Double)]]] =
    exec(Cmd(CommandType.BZPopMax, dec.decodeOptionalKeyValueScore), keys.map(encKey).toArray :+ Enc.doubleBytes(timeout))

  override def zdiffWithScores(keys: K*): F[ValkeyResponse[List[ScoredValue[V]]]] =
    exec(Cmd(CommandType.ZDiff, dec.decodeScorePairs), Array(Enc.intBytes(keys.length)) ++ keys.map(encKey) ++ Array(Enc.WITHSCORES))

  override def zunionWithScores(keys: K*): F[ValkeyResponse[List[ScoredValue[V]]]] =
    exec(Cmd(CommandType.ZUnion, dec.decodeScorePairs), Array(Enc.intBytes(keys.length)) ++ keys.map(encKey) ++ Array(Enc.WITHSCORES))

  override def zunionWithScores(keys: List[K], aggregate: AggregateOption): F[ValkeyResponse[List[ScoredValue[V]]]] =
    val args = Array(Enc.intBytes(keys.length)) ++ keys.map(encKey) ++ Array(Enc.AGGREGATE, Enc.aggregate(aggregate), Enc.WITHSCORES)
    exec(Cmd(CommandType.ZUnion, dec.decodeScorePairs), args)

  override def zinterWithScores(keys: K*): F[ValkeyResponse[List[ScoredValue[V]]]] =
    exec(Cmd(CommandType.ZInter, dec.decodeScorePairs), Array(Enc.intBytes(keys.length)) ++ keys.map(encKey) ++ Array(Enc.WITHSCORES))

  override def zinterWithScores(keys: List[K], aggregate: AggregateOption): F[ValkeyResponse[List[ScoredValue[V]]]] =
    val args = Array(Enc.intBytes(keys.length)) ++ keys.map(encKey) ++ Array(Enc.AGGREGATE, Enc.aggregate(aggregate), Enc.WITHSCORES)
    exec(Cmd(CommandType.ZInter, dec.decodeScorePairs), args)

  override def zlexcount(key: K, min: LexBoundary, max: LexBoundary): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZLexCount, Decode.long), Array(encKey(key), Enc.lexBoundary(min), Enc.lexBoundary(max)))

  override def zremrangebylex(key: K, min: LexBoundary, max: LexBoundary): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ZRemRangeByLex, Decode.long), Array(encKey(key), Enc.lexBoundary(min), Enc.lexBoundary(max)))

  override def zrangestore(destination: K, source: K, rangeQuery: RangeQuery): F[ValkeyResponse[Long]] =
    val rangeArgs: Array[Array[Byte]] = rangeQuery match
      case RangeQuery.ByIndex(start, stop) => Array(Enc.longBytes(start), Enc.longBytes(stop))
      case RangeQuery.ByScore(min, max)    => Array(Enc.scoreBoundary(min), Enc.scoreBoundary(max), Enc.BYSCORE)
      case RangeQuery.ByLex(min, max)      => Array(Enc.lexBoundary(min), Enc.lexBoundary(max), Enc.BYLEX)
    exec(Cmd(CommandType.ZRangeStore, Decode.long), Array(encKey(destination), encKey(source)) ++ rangeArgs)

  override def zrangestore(destination: K, source: K, rangeQuery: RangeQuery, reverse: Boolean): F[ValkeyResponse[Long]] =
    val rangeArgs: Array[Array[Byte]] = rangeQuery match
      case RangeQuery.ByIndex(start, stop) => Array(Enc.longBytes(start), Enc.longBytes(stop))
      case RangeQuery.ByScore(min, max)    => Array(Enc.scoreBoundary(min), Enc.scoreBoundary(max), Enc.BYSCORE)
      case RangeQuery.ByLex(min, max)      => Array(Enc.lexBoundary(min), Enc.lexBoundary(max), Enc.BYLEX)
    val revArgs: Array[Array[Byte]] = if reverse then Array(Enc.REV) else Array.empty
    exec(Cmd(CommandType.ZRangeStore, Decode.long), Array(encKey(destination), encKey(source)) ++ rangeArgs ++ revArgs)

  override def zmpop(keys: List[K], filter: ScoreFilter): F[ValkeyResponse[Option[(K, List[ScoredValue[V]])]]] =
    val args = Array(Enc.intBytes(keys.length)) ++ keys.map(encKey).toArray ++ Array(Enc.scoreFilter(filter))
    exec(Cmd(CommandType.ZMPop, dec.decodeKeyScoreListPair), args)

  override def zmpop(keys: List[K], filter: ScoreFilter, count: Long): F[ValkeyResponse[Option[(K, List[ScoredValue[V]])]]] =
    val args = Array(Enc.intBytes(keys.length)) ++ keys.map(encKey).toArray ++ Array(Enc.scoreFilter(filter), Enc.COUNT, Enc.longBytes(count))
    exec(Cmd(CommandType.ZMPop, dec.decodeKeyScoreListPair), args)

  override def bzmpop(keys: List[K], filter: ScoreFilter, timeout: Double): F[ValkeyResponse[Option[(K, List[ScoredValue[V]])]]] =
    val args = Array(Enc.doubleBytes(timeout), Enc.intBytes(keys.length)) ++ keys.map(encKey).toArray ++ Array(Enc.scoreFilter(filter))
    exec(Cmd(CommandType.BZMPop, dec.decodeKeyScoreListPair), args)

  override def bzmpop(keys: List[K], filter: ScoreFilter, timeout: Double, count: Long): F[ValkeyResponse[Option[(K, List[ScoredValue[V]])]]] =
    val args = Array(Enc.doubleBytes(timeout), Enc.intBytes(keys.length)) ++ keys.map(encKey).toArray ++ Array(Enc.scoreFilter(filter), Enc.COUNT, Enc.longBytes(count))
    exec(Cmd(CommandType.BZMPop, dec.decodeKeyScoreListPair), args)

  override def zscan(key: K, cursor: String): F[ValkeyResponse[ScanResult[List[ScoredValue[V]]]]] =
    exec(Cmd(CommandType.ZScan, dec.decodeScanScoreResult), Array(encKey(key), Enc.strBytes(cursor)))

  // ─── HyperLogLogCommands ───

  override def pfadd(key: K, elements: V*): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.PfAdd, Decode.boolean), Array(encKey(key)) ++ elements.map(encVal))

  override def pfcount(keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.PfCount, Decode.long), keys.map(encKey).toArray)

  override def pfmerge(destkey: K, sourcekeys: K*): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.PfMerge, Decode.unit), Array(encKey(destkey)) ++ sourcekeys.map(encKey))

  // ─── GeoCommands ───

  override def geoAdd(key: K, members: Map[V, GeoPosition]): F[ValkeyResponse[Long]] =
    val args = members.flatMap { (member, pos) =>
      Seq(Enc.doubleBytes(pos.longitude), Enc.doubleBytes(pos.latitude), encVal(member))
    }.toArray
    exec(Cmd(CommandType.GeoAdd, Decode.long), Array(encKey(key)) ++ args)

  override def geoAdd(key: K, members: Map[V, GeoPosition], options: GeoAddOptions): F[ValkeyResponse[Long]] =
    val condArgs = options.condition.fold(Array.empty[Array[Byte]])(Enc.geoAddCondition)
    val chArg: Array[Array[Byte]] = if options.changed then Array(Enc.CH) else Array.empty
    val memberArgs = members.flatMap { (member, pos) =>
      Seq(Enc.doubleBytes(pos.longitude), Enc.doubleBytes(pos.latitude), encVal(member))
    }.toArray
    exec(Cmd(CommandType.GeoAdd, Decode.long), Array(encKey(key)) ++ condArgs ++ chArg ++ memberArgs)

  override def geoDist(key: K, member1: V, member2: V): F[ValkeyResponse[Option[Double]]] =
    exec(Cmd(CommandType.GeoDist, dec.decodeOptionalDouble), Array(encKey(key), encVal(member1), encVal(member2)))

  override def geoDist(key: K, member1: V, member2: V, unit: GeoUnit): F[ValkeyResponse[Option[Double]]] =
    exec(Cmd(CommandType.GeoDist, dec.decodeOptionalDouble), Array(encKey(key), encVal(member1), encVal(member2), Enc.geoUnit(unit)))

  override def geoHash(key: K, members: V*): F[ValkeyResponse[List[Option[String]]]] =
    exec(Cmd(CommandType.GeoHash, Decode.optionalList(Codec.utf8Codec)), Array(encKey(key)) ++ members.map(encVal))

  override def geoPos(key: K, members: V*): F[ValkeyResponse[List[Option[GeoPosition]]]] =
    exec(Cmd(CommandType.GeoPos, dec.decodeGeoPositions), Array(encKey(key)) ++ members.map(encVal))

  private def encGeoSearchFrom(from: GeoSearchFrom[K]): Array[Array[Byte]] = from match
    case GeoSearchFrom.FromMember(member) => Array(Enc.FROMMEMBER, encKey(member))
    case GeoSearchFrom.FromCoord(pos)     => Array(Enc.FROMLONLAT, Enc.doubleBytes(pos.longitude), Enc.doubleBytes(pos.latitude))

  override def geoSearch(key: K, from: GeoSearchFrom[K], by: GeoSearchBy): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.GeoSearch, Decode.list(valueCodec)), Array(encKey(key)) ++ encGeoSearchFrom(from) ++ Enc.geoSearchBy(by))

  override def geoSearch(key: K, from: GeoSearchFrom[K], by: GeoSearchBy, resultOptions: GeoSearchResultOptions): F[ValkeyResponse[List[V]]] =
    exec(Cmd(CommandType.GeoSearch, Decode.list(valueCodec)), Array(encKey(key)) ++ encGeoSearchFrom(from) ++ Enc.geoSearchBy(by) ++ Enc.geoResultOptions(resultOptions))

  override def geoSearchStore(destination: K, source: K, from: GeoSearchFrom[K], by: GeoSearchBy): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.GeoSearchStore, Decode.long), Array(encKey(destination), encKey(source)) ++ encGeoSearchFrom(from) ++ Enc.geoSearchBy(by))

  override def geoSearchStore(destination: K, source: K, from: GeoSearchFrom[K], by: GeoSearchBy, resultOptions: GeoSearchResultOptions): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.GeoSearchStore, Decode.long), Array(encKey(destination), encKey(source)) ++ encGeoSearchFrom(from) ++ Enc.geoSearchBy(by) ++ Enc.geoResultOptions(resultOptions))

  // ─── BitmapCommands ───

  override def setbit(key: K, offset: Long, value: Long): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.SetBit, Decode.long), Array(encKey(key), Enc.longBytes(offset), Enc.longBytes(value)))

  override def getbit(key: K, offset: Long): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.GetBit, Decode.long), Array(encKey(key), Enc.longBytes(offset)))

  override def bitcount(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.BitCount, Decode.long), Array(encKey(key)))

  override def bitcount(key: K, start: Long, end: Long): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.BitCount, Decode.long), Array(encKey(key), Enc.longBytes(start), Enc.longBytes(end)))

  override def bitcount(key: K, start: Long, end: Long, indexType: BitmapIndexType): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.BitCount, Decode.long), Array(encKey(key), Enc.longBytes(start), Enc.longBytes(end), Enc.bitmapIndexType(indexType)))

  override def bitpos(key: K, bit: Long): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.BitPos, Decode.long), Array(encKey(key), Enc.longBytes(bit)))

  override def bitpos(key: K, bit: Long, start: Long): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.BitPos, Decode.long), Array(encKey(key), Enc.longBytes(bit), Enc.longBytes(start)))

  override def bitpos(key: K, bit: Long, start: Long, end: Long): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.BitPos, Decode.long), Array(encKey(key), Enc.longBytes(bit), Enc.longBytes(start), Enc.longBytes(end)))

  override def bitpos(key: K, bit: Long, start: Long, end: Long, indexType: BitmapIndexType): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.BitPos, Decode.long), Array(encKey(key), Enc.longBytes(bit), Enc.longBytes(start), Enc.longBytes(end), Enc.bitmapIndexType(indexType)))

  override def bitop(operation: BitwiseOperation, destkey: K, keys: K*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.BitOp, Decode.long), Array(Enc.bitwiseOp(operation), encKey(destkey)) ++ keys.map(encKey))

  // ─── PubSubCommands ───

  override def publish(channel: K, message: V): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.Publish, Decode.unit), Array(encKey(channel), encVal(message)))

  override def pubsubChannels: F[ValkeyResponse[List[K]]] =
    exec(Cmd(CommandType.PubSubChannels, Decode.list(keyCodec)), Array.empty)

  override def pubsubChannels(pattern: K): F[ValkeyResponse[List[K]]] =
    exec(Cmd(CommandType.PubSubChannels, Decode.list(keyCodec)), Array(encKey(pattern)))

  override def pubsubNumPat: F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.PubSubNumPat, Decode.long), Array.empty)

  override def pubsubNumSub(channels: K*): F[ValkeyResponse[Map[K, Long]]] =
    exec(Cmd(CommandType.PubSubNumSub, dec.decodeMapKeyLong), channels.map(encKey).toArray)

  // ─── StreamCommands ───

  override def xadd(key: K, fieldValues: Map[K, V]): F[ValkeyResponse[String]] =
    val fvArgs = fieldValues.flatMap((k, v) => Seq(encKey(k), encVal(v))).toArray
    exec(Cmd(CommandType.XAdd, Decode.string), Array(encKey(key), Enc.STAR) ++ fvArgs)

  override def xlen(key: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.XLen, Decode.long), Array(encKey(key)))

  override def xdel(key: K, ids: String*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.XDel, Decode.long), Array(encKey(key)) ++ ids.map(Enc.strBytes))

  override def xtrim(key: K, strategy: StreamTrimStrategy): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.XTrim, Decode.long), Array(encKey(key)) ++ Enc.streamTrimStrategy(strategy))

  override def xrange(key: K, start: StreamRangeBound, end: StreamRangeBound): F[ValkeyResponse[Map[String, List[(K, V)]]]] =
    exec(Cmd(CommandType.XRange, dec.decodeStreamEntries), Array(encKey(key), Enc.streamBound(start), Enc.streamBound(end)))

  override def xrange(key: K, start: StreamRangeBound, end: StreamRangeBound, count: Long): F[ValkeyResponse[Map[String, List[(K, V)]]]] =
    exec(Cmd(CommandType.XRange, dec.decodeStreamEntries), Array(encKey(key), Enc.streamBound(start), Enc.streamBound(end), Enc.COUNT, Enc.longBytes(count)))

  override def xrevrange(key: K, end: StreamRangeBound, start: StreamRangeBound): F[ValkeyResponse[Map[String, List[(K, V)]]]] =
    exec(Cmd(CommandType.XRevRange, dec.decodeStreamEntries), Array(encKey(key), Enc.streamBound(end), Enc.streamBound(start)))

  override def xrevrange(key: K, end: StreamRangeBound, start: StreamRangeBound, count: Long): F[ValkeyResponse[Map[String, List[(K, V)]]]] =
    exec(Cmd(CommandType.XRevRange, dec.decodeStreamEntries), Array(encKey(key), Enc.streamBound(end), Enc.streamBound(start), Enc.COUNT, Enc.longBytes(count)))

  override def xgroupCreate(key: K, group: K, id: String): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.XGroupCreate, Decode.unit), Array(encKey(key), encKey(group), Enc.strBytes(id)))

  override def xgroupCreate(key: K, group: K, id: String, mkStream: Boolean): F[ValkeyResponse[Unit]] =
    val args = if mkStream then Array(encKey(key), encKey(group), Enc.strBytes(id), Enc.MKSTREAM)
               else Array(encKey(key), encKey(group), Enc.strBytes(id))
    exec(Cmd(CommandType.XGroupCreate, Decode.unit), args)

  override def xgroupDestroy(key: K, group: K): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.XGroupDestroy, Decode.boolean), Array(encKey(key), encKey(group)))

  override def xgroupCreateConsumer(key: K, group: K, consumer: K): F[ValkeyResponse[Boolean]] =
    exec(Cmd(CommandType.XGroupCreateConsumer, Decode.boolean), Array(encKey(key), encKey(group), encKey(consumer)))

  override def xgroupDelConsumer(key: K, group: K, consumer: K): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.XGroupDelConsumer, Decode.long), Array(encKey(key), encKey(group), encKey(consumer)))

  override def xgroupSetId(key: K, group: K, id: String): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.XGroupSetId, Decode.unit), Array(encKey(key), encKey(group), Enc.strBytes(id)))

  override def xack(key: K, group: K, ids: String*): F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.XAck, Decode.long), Array(encKey(key), encKey(group)) ++ ids.map(Enc.strBytes))

  override def xread(keysAndIds: Map[K, String]): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]] =
    val keys = keysAndIds.keys.map(encKey).toArray
    val ids = keysAndIds.values.map(Enc.strBytes).toArray
    exec(Cmd(CommandType.XRead, dec.decodeXReadResult), Array(Enc.STREAMS) ++ keys ++ ids)

  override def xread(keysAndIds: Map[K, String], count: Long, block: Long): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]] =
    val keys = keysAndIds.keys.map(encKey).toArray
    val ids = keysAndIds.values.map(Enc.strBytes).toArray
    exec(Cmd(CommandType.XRead, dec.decodeXReadResult), Array(Enc.COUNT, Enc.longBytes(count), Enc.BLOCK, Enc.longBytes(block), Enc.STREAMS) ++ keys ++ ids)

  override def xreadgroup(group: K, consumer: K, keysAndIds: Map[K, String]): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]] =
    val keys = keysAndIds.keys.map(encKey).toArray
    val ids = keysAndIds.values.map(Enc.strBytes).toArray
    exec(Cmd(CommandType.XReadGroup, dec.decodeXReadResult), Array(Enc.GROUP, encKey(group), encKey(consumer), Enc.STREAMS) ++ keys ++ ids)

  override def xreadgroup(group: K, consumer: K, keysAndIds: Map[K, String], count: Long, block: Long): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]] =
    val keys = keysAndIds.keys.map(encKey).toArray
    val ids = keysAndIds.values.map(Enc.strBytes).toArray
    exec(Cmd(CommandType.XReadGroup, dec.decodeXReadResult), Array(Enc.GROUP, encKey(group), encKey(consumer), Enc.COUNT, Enc.longBytes(count), Enc.BLOCK, Enc.longBytes(block), Enc.STREAMS) ++ keys ++ ids)

  override def xreadgroup(group: K, consumer: K, keysAndIds: Map[K, String], count: Long, block: Long, noAck: Boolean): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]] =
    val keys = keysAndIds.keys.map(encKey).toArray
    val ids = keysAndIds.values.map(Enc.strBytes).toArray
    val noAckArg: Array[Array[Byte]] = if noAck then Array(Enc.NOACK) else Array.empty
    exec(Cmd(CommandType.XReadGroup, dec.decodeXReadResult), Array(Enc.GROUP, encKey(group), encKey(consumer), Enc.COUNT, Enc.longBytes(count), Enc.BLOCK, Enc.longBytes(block)) ++ noAckArg ++ Array(Enc.STREAMS) ++ keys ++ ids)

  override def xclaim(key: K, group: K, consumer: K, minIdleTimeMillis: Long, ids: String*): F[ValkeyResponse[Map[String, List[(K, V)]]]] =
    val args = Array(encKey(key), encKey(group), encKey(consumer), Enc.longBytes(minIdleTimeMillis)) ++ ids.map(Enc.strBytes)
    exec(Cmd(CommandType.XClaim, dec.decodeStreamEntries), args)

  override def xpendingSummary(key: K, group: K): F[ValkeyResponse[PendingSummary[K]]] =
    exec(Cmd(CommandType.XPending, dec.decodePendingSummary), Array(encKey(key), encKey(group)))

  override def xpendingRange(key: K, group: K, start: StreamRangeBound, end: StreamRangeBound, count: Long): F[ValkeyResponse[List[PendingEntry[K]]]] =
    exec(Cmd(CommandType.XPending, dec.decodePendingRange), Array(encKey(key), encKey(group), Enc.streamBound(start), Enc.streamBound(end), Enc.longBytes(count)))

  override def xautoclaim(key: K, group: K, consumer: K, minIdleTimeMillis: Long, start: String): F[ValkeyResponse[AutoClaimResult[K, V]]] =
    val args = Array(encKey(key), encKey(group), encKey(consumer), Enc.longBytes(minIdleTimeMillis), Enc.strBytes(start))
    exec(Cmd(CommandType.XAutoClaim, dec.decodeAutoClaimResult), args)

  override def xautoclaim(key: K, group: K, consumer: K, minIdleTimeMillis: Long, start: String, count: Long): F[ValkeyResponse[AutoClaimResult[K, V]]] =
    val args = Array(encKey(key), encKey(group), encKey(consumer), Enc.longBytes(minIdleTimeMillis), Enc.strBytes(start), Enc.COUNT, Enc.longBytes(count))
    exec(Cmd(CommandType.XAutoClaim, dec.decodeAutoClaimResult), args)

  override def xautoclaimJustId(key: K, group: K, consumer: K, minIdleTimeMillis: Long, start: String): F[ValkeyResponse[AutoClaimIdResult]] =
    val args = Array(encKey(key), encKey(group), encKey(consumer), Enc.longBytes(minIdleTimeMillis), Enc.strBytes(start), Enc.JUSTID)
    exec(Cmd(CommandType.XAutoClaim, dec.decodeAutoClaimIdResult), args)

  // ─── ScriptingCommands ───

  override def fcall(function: K, keys: List[K], args: List[K]): F[ValkeyResponse[String]] =
    val allArgs = Array(encKey(function), Enc.intBytes(keys.length)) ++ keys.map(encKey) ++ args.map(encKey)
    exec(Cmd(CommandType.FCall, Decode.string), allArgs)

  override def fcallReadOnly(function: K, keys: List[K], args: List[K]): F[ValkeyResponse[String]] =
    val allArgs = Array(encKey(function), Enc.intBytes(keys.length)) ++ keys.map(encKey) ++ args.map(encKey)
    exec(Cmd(CommandType.FCallReadOnly, Decode.string), allArgs)

  override def scriptFlush: F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.ScriptFlush, Decode.unit), Array.empty)

  override def scriptFlush(mode: FlushMode): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.ScriptFlush, Decode.unit), Array(Enc.flushMode(mode)))

  override def scriptKill: F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.ScriptKill, Decode.unit), Array.empty)

  override def scriptExists(sha1s: String*): F[ValkeyResponse[List[Boolean]]] =
    exec(Cmd(CommandType.ScriptExists, dec.decodeBooleanList), sha1s.map(Enc.strBytes).toArray)

  // ─── ServerCommands ───

  override def info: F[ValkeyResponse[String]] =
    exec(Cmd(CommandType.Info, decodeInfoResponse), Array.empty)

  override def info(sections: Set[InfoSection]): F[ValkeyResponse[String]] =
    exec(Cmd(CommandType.Info, decodeInfoResponse), sections.map(s => Enc.strBytes(s.toString.toLowerCase)).toArray)

  private val decodeInfoResponse: MemorySegment => String = { seg =>
    val direct = ResponseParser.decodeString(seg)
    if direct.nonEmpty then direct
    else Decode.mapStringString(seg).values.mkString("\n")
  }

  override def configRewrite: F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.ConfigRewrite, Decode.unit), Array.empty)

  override def configResetStat: F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.ConfigResetStat, Decode.unit), Array.empty)

  override def configGet(parameters: Set[String]): F[ValkeyResponse[Map[String, String]]] =
    exec(Cmd(CommandType.ConfigGet, Decode.mapStringString), parameters.map(Enc.strBytes).toArray)

  override def configSet(parameters: Map[String, String]): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.ConfigSet, Decode.unit), parameters.flatMap((k, v) => Seq(Enc.strBytes(k), Enc.strBytes(v))).toArray)

  override def time: F[ValkeyResponse[ServerTime]] =
    exec(Cmd(CommandType.Time, { seg =>
      val list = ResponseParser.decodeList(seg, Codec.utf8Codec)
      ServerTime(list(0).toLong, list(1).toLong)
    }), Array.empty)

  override def lastSave: F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.LastSave, Decode.long), Array.empty)

  override def flushAll: F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.FlushAll, Decode.unit), Array.empty)

  override def flushAll(mode: FlushMode): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.FlushAll, Decode.unit), Array(Enc.flushMode(mode)))

  override def flushDB: F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.FlushDb, Decode.unit), Array.empty)

  override def flushDB(mode: FlushMode): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.FlushDb, Decode.unit), Array(Enc.flushMode(mode)))

  override def lolwut: F[ValkeyResponse[String]] =
    exec(Cmd(CommandType.Lolwut, Decode.string), Array.empty)

  override def lolwut(version: Int): F[ValkeyResponse[String]] =
    exec(Cmd(CommandType.Lolwut, Decode.string), Array(Enc.intBytes(version)))

  override def lolwut(version: Int, parameters: List[Int]): F[ValkeyResponse[String]] =
    exec(Cmd(CommandType.Lolwut, Decode.string), Array(Enc.intBytes(version)) ++ parameters.map(Enc.intBytes))

  override def dbSize: F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.DbSize, Decode.long), Array.empty)

  // ─── ConnectionCommands ───

  override def ping: F[ValkeyResponse[String]] =
    exec(Cmd(CommandType.Ping, Decode.string), Array.empty)

  override def ping(message: V): F[ValkeyResponse[V]] =
    exec(Cmd(CommandType.Ping, Decode.value(valueCodec)), Array(encVal(message)))

  override def echo(message: V): F[ValkeyResponse[V]] =
    exec(Cmd(CommandType.Echo, Decode.value(valueCodec)), Array(encVal(message)))

  override def clientId: F[ValkeyResponse[Long]] =
    exec(Cmd(CommandType.ClientId, Decode.long), Array.empty)

  override def clientGetName: F[ValkeyResponse[Option[String]]] =
    exec(Cmd(CommandType.ClientGetName, Decode.optional(Codec.utf8Codec)), Array.empty)

  override def select(index: Long): F[ValkeyResponse[Unit]] =
    exec(Cmd(CommandType.Select, Decode.unit), Array(Enc.longBytes(index)))
