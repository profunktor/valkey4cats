package dev.profunktor.valkey4cats

import cats.effect.*
import cats.syntax.all.*
import glide.api.BaseClient
import glide.api.models.exceptions.{ExecAbortException, RequestException}
import dev.profunktor.valkey4cats.arguments.{
  AggregateOption,
  BitmapIndexType,
  BitwiseOperation,
  ExpireCondition,
  ExpirySet,
  FieldCondition,
  FlushMode,
  GeoAddOptions,
  GeoPosition,
  GeoSearchBy,
  GeoSearchFrom,
  GeoSearchResultOptions,
  GeoUnit,
  GetExExpiry,
  HGetExExpiry,
  InfoSection,
  InsertPosition,
  LexBoundary,
  ListDirection,
  RangeQuery,
  ScoreFilter,
  SetOptions,
  StreamRangeBound,
  StreamTrimStrategy,
  ZAddOptions
}
import dev.profunktor.valkey4cats.codec.Codec
import dev.profunktor.valkey4cats.connection.{
  ValkeyClient,
  ValkeyClusterClient,
  ValkeyConnection
}
import dev.profunktor.valkey4cats.effect.FutureLift.FutureLiftOps
import dev.profunktor.valkey4cats.effect.{FutureLift, Log, MkValkey}
import dev.profunktor.valkey4cats.model.{ValkeyError, ValkeyResponse}
import dev.profunktor.valkey4cats.results.{
  AutoClaimIdResult,
  AutoClaimResult,
  ClusterScanCursor,
  ClusterScanResult,
  InsertResult,
  PendingEntry,
  PendingSummary,
  ScanResult,
  ServerTime,
  SetResult
}
import dev.profunktor.valkey4cats.tx.TxRunner

import scala.jdk.CollectionConverters.*

/** Base implementation for Valkey commands
  *
  * Supports both standalone and cluster clients through the ValkeyConnection ADT
  *
  * @param connection Standalone or cluster connection
  * @param keyCodec Codec for encoding/decoding keys
  * @param valueCodec Codec for encoding/decoding values
  * @param tx Transaction runner (stub for Phase 1)
  */
private[valkey4cats] abstract class BaseValkey[F[_]: MkValkey, K, V](
    protected val connection: ValkeyConnection,
    protected val keyCodec: Codec[K],
    protected val valueCodec: Codec[V],
    protected val tx: TxRunner[F]
)(implicit F: Async[F])
    extends ValkeyCommands[F, K, V] {

  /** Get capabilities from MkValkey */
  private implicit val futureLift: FutureLift[F] = MkValkey[F].futureLift
  private implicit val logger: Log[F] = MkValkey[F].log

  /** Get the underlying Glide BaseClient (works for both standalone and cluster) */
  private val baseClient: BaseClient = connection.baseClient

  private def optDecodeV(gs: glide.api.models.GlideString): Option[V] =
    Option(gs).map(valueCodec.decode)

  private def optDecodeK(gs: glide.api.models.GlideString): Option[K] =
    Option(gs).map(keyCodec.decode)

  /** Execute a command, catching domain errors into ValkeyResponse.Err
    * and letting infrastructure errors propagate in F.
    */
  private def exec[A](cmd: String)(fa: F[A]): F[ValkeyResponse[A]] =
    fa.map(ValkeyResponse.ok).handleErrorWith {
      case e: RequestException =>
        Log[F].error(s"Error in $cmd: ${e.getMessage}") *>
          Async[F].pure(
            ValkeyResponse.err(ValkeyError.fromMessage(e.getMessage))
          )
      case e: ExecAbortException =>
        Log[F].error(s"Error in $cmd: ${e.getMessage}") *>
          Async[F].pure(
            ValkeyResponse.err(ValkeyError.TransactionAborted(e.getMessage))
          )
      case e =>
        Log[F].error(s"Error in $cmd: ${e.getMessage}") *>
          Async[F].raiseError(e)
    }

  /** Execute a server command that exists on both GlideClient and GlideClusterClient
    * but not on BaseClient, by dispatching through the connection ADT.
    */
  private def serverCmd[A](cmd: String)(
      standalone: glide.api.GlideClient => F[A],
      cluster: glide.api.GlideClusterClient => F[A]
  ): F[ValkeyResponse[A]] =
    exec(cmd) {
      connection match {
        case ValkeyConnection.Standalone(c) => standalone(c.underlying)
        case ValkeyConnection.Clustered(c)  => cluster(c.underlying)
      }
    }

  // ==================== String Commands ====================

  override def get(key: K): F[ValkeyResponse[Option[V]]] = exec(s"GET $key") {
    val keyGS = keyCodec.encode(key)
    baseClient
      .get(keyGS)
      .futureLift
      .map(optDecodeV)
  }

  override def set(key: K, value: V): F[ValkeyResponse[Unit]] =
    exec(s"SET $key") {
      val keyGS = keyCodec.encode(key)
      val valueGS = valueCodec.encode(value)
      baseClient.set(keyGS, valueGS).futureLift.void
    }

  override def set(
      key: K,
      value: V,
      options: SetOptions
  ): F[ValkeyResponse[SetResult[V]]] =
    exec(s"SET $key with options") {
      val keyGS = keyCodec.encode(key)
      val valueGS = valueCodec.encode(value)
      val glideOptions = SetOptions.toGlide(options)
      baseClient
        .set(keyGS, valueGS, glideOptions)
        .futureLift
        .map { result =>
          if (result == null) {
            // null means the NX/XX condition was not met
            SetResult.NotSet
          } else if (result == "OK") {
            SetResult.Written
          } else if (options.returnOldValue) {
            SetResult.Replaced(
              valueCodec.decode(glide.api.models.GlideString.of(result))
            )
          } else {
            SetResult.Written
          }
        }
    }

  override def mGet(keys: Set[K]): F[ValkeyResponse[Map[K, V]]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(Map.empty[K, V]))
    else
      exec("MGET") {
        val keysList = keys.toList
        val keysArray = keysList.map(k => keyCodec.encode(k)).toArray
        baseClient
          .mget(keysArray)
          .futureLift
          .map { javaArray =>
            keysList
              .zip(javaArray.toList)
              .collect {
                case (key, value) if value != null =>
                  key -> valueCodec.decode(value)
              }
              .toMap
          }
      }
  }

  override def mSet(keyValues: Map[K, V]): F[ValkeyResponse[Unit]] = {
    if (keyValues.isEmpty) Async[F].pure(ValkeyResponse.ok(()))
    else
      exec("MSET") {
        val javaMap = keyValues.map { case (k, v) =>
          new String(keyCodec.encode(k).getBytes()) -> new String(
            valueCodec.encode(v).getBytes()
          )
        }.asJava
        baseClient.mset(javaMap).futureLift.void
      }
  }

  override def incr(key: K): F[ValkeyResponse[Long]] = exec(s"INCR $key") {
    baseClient.incr(keyCodec.encode(key)).futureLift.map(_.longValue())
  }

  override def incrBy(key: K, amount: Long): F[ValkeyResponse[Long]] =
    exec(s"INCRBY $key $amount") {
      baseClient
        .incrBy(keyCodec.encode(key), amount)
        .futureLift
        .map(_.longValue())
    }

  override def decr(key: K): F[ValkeyResponse[Long]] = exec(s"DECR $key") {
    baseClient.decr(keyCodec.encode(key)).futureLift.map(_.longValue())
  }

  override def decrBy(key: K, amount: Long): F[ValkeyResponse[Long]] =
    exec(s"DECRBY $key $amount") {
      baseClient
        .decrBy(keyCodec.encode(key), amount)
        .futureLift
        .map(_.longValue())
    }

  override def append(key: K, value: V): F[ValkeyResponse[Long]] =
    exec(s"APPEND $key") {
      baseClient
        .append(keyCodec.encode(key), valueCodec.encode(value))
        .futureLift
        .map(_.longValue())
    }

  override def strlen(key: K): F[ValkeyResponse[Long]] = exec(s"STRLEN $key") {
    baseClient.strlen(keyCodec.encode(key)).futureLift.map(_.longValue())
  }

  override def getEx(key: K): F[ValkeyResponse[Option[V]]] =
    exec(s"GETEX $key") {
      baseClient
        .getex(keyCodec.encode(key))
        .futureLift
        .map(optDecodeV)
    }

  override def getEx(
      key: K,
      expiry: GetExExpiry
  ): F[ValkeyResponse[Option[V]]] =
    exec(s"GETEX $key") {
      baseClient
        .getex(keyCodec.encode(key), expiry.toGlide)
        .futureLift
        .map(optDecodeV)
    }

  override def getDel(key: K): F[ValkeyResponse[Option[V]]] =
    exec(s"GETDEL $key") {
      baseClient
        .getdel(keyCodec.encode(key))
        .futureLift
        .map(optDecodeV)
    }

  override def incrByFloat(key: K, amount: Double): F[ValkeyResponse[Double]] =
    exec(s"INCRBYFLOAT $key") {
      baseClient
        .incrByFloat(keyCodec.encode(key), amount)
        .futureLift
        .map(_.doubleValue())
    }

  override def setNx(key: K, value: V): F[ValkeyResponse[Boolean]] =
    exec(s"SET $key NX") {
      val keyGS = keyCodec.encode(key)
      val valueGS = valueCodec.encode(value)
      val opts = glide.api.models.commands.SetOptions
        .builder()
        .conditionalSetOnlyIfNotExist()
        .build()
      baseClient
        .set(keyGS, valueGS, opts)
        .futureLift
        .map(_ != null)
    }

  override def mSetNx(keyValues: Map[K, V]): F[ValkeyResponse[Boolean]] = {
    if (keyValues.isEmpty) Async[F].pure(ValkeyResponse.ok(true))
    else
      exec("MSETNX") {
        val map = new java.util.HashMap[
          glide.api.models.GlideString,
          glide.api.models.GlideString
        ]()
        keyValues.foreach { case (k, v) =>
          map.put(keyCodec.encode(k), valueCodec.encode(v))
        }
        baseClient
          .msetnxBinary(map)
          .futureLift
          .map(_.booleanValue())
      }
  }

  override def getRange(
      key: K,
      start: Long,
      end: Long
  ): F[ValkeyResponse[V]] =
    exec(s"GETRANGE $key") {
      baseClient
        .getrange(keyCodec.encode(key), start.toInt, end.toInt)
        .futureLift
        .map(gs => valueCodec.decode(gs))
    }

  override def setRange(
      key: K,
      offset: Long,
      value: V
  ): F[ValkeyResponse[Long]] =
    exec(s"SETRANGE $key") {
      baseClient
        .setrange(keyCodec.encode(key), offset.toInt, valueCodec.encode(value))
        .futureLift
        .map(_.longValue())
    }

  override def lcs(key1: K, key2: K): F[ValkeyResponse[V]] =
    exec(s"LCS $key1 $key2") {
      baseClient
        .lcs(keyCodec.encode(key1), keyCodec.encode(key2))
        .futureLift
        .map(valueCodec.decode)
    }

  override def lcsLen(key1: K, key2: K): F[ValkeyResponse[Long]] =
    exec(s"LCS LEN $key1 $key2") {
      baseClient
        .lcsLen(keyCodec.encode(key1), keyCodec.encode(key2))
        .futureLift
        .map(_.longValue())
    }

  // ==================== Key Commands ====================

  override def del(keys: K*): F[ValkeyResponse[Long]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec("DEL") {
        baseClient
          .del(keys.map(keyCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def exists(key: K): F[ValkeyResponse[Boolean]] =
    exec(s"EXISTS $key") {
      baseClient
        .exists(Array(keyCodec.encode(key)))
        .futureLift
        .map(_.longValue() == 1L)
    }

  override def existsMany(keys: K*): F[ValkeyResponse[Long]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec("EXISTS") {
        baseClient
          .exists(keys.map(keyCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def unlink(keys: K*): F[ValkeyResponse[Long]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec("UNLINK") {
        baseClient
          .unlink(keys.map(keyCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def expire(key: K, seconds: Long): F[ValkeyResponse[Boolean]] =
    exec(s"EXPIRE $key") {
      baseClient
        .expire(keyCodec.encode(key), seconds)
        .futureLift
        .map(_.booleanValue())
    }

  override def expire(
      key: K,
      seconds: Long,
      condition: ExpireCondition
  ): F[ValkeyResponse[Boolean]] =
    exec(s"EXPIRE $key") {
      baseClient
        .expire(keyCodec.encode(key), seconds, condition.toGlide)
        .futureLift
        .map(_.booleanValue())
    }

  override def pexpire(key: K, milliseconds: Long): F[ValkeyResponse[Boolean]] =
    exec(s"PEXPIRE $key") {
      baseClient
        .pexpire(keyCodec.encode(key), milliseconds)
        .futureLift
        .map(_.booleanValue())
    }

  override def pexpire(
      key: K,
      milliseconds: Long,
      condition: ExpireCondition
  ): F[ValkeyResponse[Boolean]] =
    exec(s"PEXPIRE $key") {
      baseClient
        .pexpire(keyCodec.encode(key), milliseconds, condition.toGlide)
        .futureLift
        .map(_.booleanValue())
    }

  override def expireAt(key: K, unixSeconds: Long): F[ValkeyResponse[Boolean]] =
    exec(s"EXPIREAT $key") {
      baseClient
        .expireAt(keyCodec.encode(key), unixSeconds)
        .futureLift
        .map(_.booleanValue())
    }

  override def expireAt(
      key: K,
      unixSeconds: Long,
      condition: ExpireCondition
  ): F[ValkeyResponse[Boolean]] =
    exec(s"EXPIREAT $key") {
      baseClient
        .expireAt(keyCodec.encode(key), unixSeconds, condition.toGlide)
        .futureLift
        .map(_.booleanValue())
    }

  override def pexpireAt(
      key: K,
      unixMilliseconds: Long
  ): F[ValkeyResponse[Boolean]] =
    exec(s"PEXPIREAT $key") {
      baseClient
        .pexpireAt(keyCodec.encode(key), unixMilliseconds)
        .futureLift
        .map(_.booleanValue())
    }

  override def pexpireAt(
      key: K,
      unixMilliseconds: Long,
      condition: ExpireCondition
  ): F[ValkeyResponse[Boolean]] =
    exec(s"PEXPIREAT $key") {
      baseClient
        .pexpireAt(keyCodec.encode(key), unixMilliseconds, condition.toGlide)
        .futureLift
        .map(_.booleanValue())
    }

  override def ttl(key: K): F[ValkeyResponse[Long]] =
    exec(s"TTL $key") {
      baseClient
        .ttl(keyCodec.encode(key))
        .futureLift
        .map(_.longValue())
    }

  override def pttl(key: K): F[ValkeyResponse[Long]] =
    exec(s"PTTL $key") {
      baseClient
        .pttl(keyCodec.encode(key))
        .futureLift
        .map(_.longValue())
    }

  override def expireTime(key: K): F[ValkeyResponse[Long]] =
    exec(s"EXPIRETIME $key") {
      baseClient
        .expiretime(keyCodec.encode(key))
        .futureLift
        .map(_.longValue())
    }

  override def pexpireTime(key: K): F[ValkeyResponse[Long]] =
    exec(s"PEXPIRETIME $key") {
      baseClient
        .pexpiretime(keyCodec.encode(key))
        .futureLift
        .map(_.longValue())
    }

  override def persist(key: K): F[ValkeyResponse[Boolean]] =
    exec(s"PERSIST $key") {
      baseClient
        .persist(keyCodec.encode(key))
        .futureLift
        .map(_.booleanValue())
    }

  override def rename(key: K, newKey: K): F[ValkeyResponse[Unit]] =
    exec(s"RENAME $key") {
      baseClient
        .rename(keyCodec.encode(key), keyCodec.encode(newKey))
        .futureLift
        .void
    }

  override def renameNx(key: K, newKey: K): F[ValkeyResponse[Boolean]] =
    exec(s"RENAMENX $key") {
      baseClient
        .renamenx(keyCodec.encode(key), keyCodec.encode(newKey))
        .futureLift
        .map(_.booleanValue())
    }

  override def typeOf(key: K): F[ValkeyResponse[String]] =
    exec(s"TYPE $key") {
      baseClient
        .`type`(keyCodec.encode(key))
        .futureLift
    }

  override def objectEncoding(key: K): F[ValkeyResponse[Option[String]]] =
    exec(s"OBJECT ENCODING $key") {
      baseClient
        .objectEncoding(keyCodec.encode(key))
        .futureLift
        .map(Option(_))
    }

  override def touch(keys: K*): F[ValkeyResponse[Long]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec("TOUCH") {
        baseClient
          .touch(keys.map(keyCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def copy(source: K, destination: K): F[ValkeyResponse[Boolean]] =
    exec(s"COPY $source") {
      baseClient
        .copy(keyCodec.encode(source), keyCodec.encode(destination))
        .futureLift
        .map(_.booleanValue())
    }

  override def randomKey: F[ValkeyResponse[Option[K]]] =
    serverCmd("RANDOMKEY")(
      _.randomKeyBinary().futureLift.map(optDecodeK),
      _.randomKeyBinary().futureLift.map(optDecodeK)
    )

  override def objectFreq(key: K): F[ValkeyResponse[Option[Long]]] =
    exec(s"OBJECT FREQ $key") {
      baseClient
        .objectFreq(keyCodec.encode(key))
        .futureLift
        .map(l => Option(l).map(_.longValue()))
    }

  override def objectIdletime(key: K): F[ValkeyResponse[Option[Long]]] =
    exec(s"OBJECT IDLETIME $key") {
      baseClient
        .objectIdletime(keyCodec.encode(key))
        .futureLift
        .map(l => Option(l).map(_.longValue()))
    }

  override def objectRefcount(key: K): F[ValkeyResponse[Option[Long]]] =
    exec(s"OBJECT REFCOUNT $key") {
      baseClient
        .objectRefcount(keyCodec.encode(key))
        .futureLift
        .map(l => Option(l).map(_.longValue()))
    }

  override def sort(key: K): F[ValkeyResponse[List[V]]] =
    exec(s"SORT $key") {
      baseClient
        .sort(keyCodec.encode(key))
        .futureLift
        .map(_.toList.map(valueCodec.decode))
    }

  override def sortReadOnly(key: K): F[ValkeyResponse[List[V]]] =
    exec(s"SORT_RO $key") {
      baseClient
        .sortReadOnly(keyCodec.encode(key))
        .futureLift
        .map(_.toList.map(valueCodec.decode))
    }

  override def sortStore(key: K, destination: K): F[ValkeyResponse[Long]] =
    exec(s"SORT STORE $key") {
      baseClient
        .sortStore(keyCodec.encode(key), keyCodec.encode(destination))
        .futureLift
        .map(_.longValue())
    }

  override def dump(key: K): F[ValkeyResponse[Option[Array[Byte]]]] =
    exec(s"DUMP $key") {
      baseClient
        .dump(keyCodec.encode(key))
        .futureLift
        .map(b => Option(b))
    }

  override def restore(
      key: K,
      ttlMillis: Long,
      serializedValue: Array[Byte]
  ): F[ValkeyResponse[Unit]] =
    exec(s"RESTORE $key") {
      baseClient
        .restore(keyCodec.encode(key), ttlMillis, serializedValue)
        .futureLift
        .void
    }

  override def waitReplicas(
      numReplicas: Long,
      timeout: Long
  ): F[ValkeyResponse[Long]] =
    exec(s"WAIT $numReplicas $timeout") {
      baseClient
        .`wait`(numReplicas, timeout)
        .futureLift
        .map(_.longValue())
    }

  override def move(key: K, db: Long): F[ValkeyResponse[Boolean]] =
    exec(s"MOVE $key $db") {
      baseClient
        .move(keyCodec.encode(key), db)
        .futureLift
        .map(_.booleanValue())
    }

  private def parseScanResult(result: Array[Object]): ScanResult[List[K]] = {
    val nextCursor =
      result(0).asInstanceOf[glide.api.models.GlideString].getString
    val keys =
      result(1)
        .asInstanceOf[Array[Object]]
        .toList
        .map(o => keyCodec.decode(o.asInstanceOf[glide.api.models.GlideString]))
    ScanResult(nextCursor, keys)
  }

  override def scan(cursor: String): F[ValkeyResponse[ScanResult[List[K]]]] =
    serverCmd("SCAN")(
      _.scan(glide.api.models.GlideString.of(cursor)).futureLift
        .map(parseScanResult),
      _ =>
        Async[F].raiseError(
          new UnsupportedOperationException(
            "Cluster SCAN with string cursor is not supported; use standalone mode"
          )
        )
    )

  override def scan(
      cursor: String,
      matchPattern: String,
      count: Long
  ): F[ValkeyResponse[ScanResult[List[K]]]] = {
    val opts = glide.api.models.commands.scan.ScanOptions
      .builder()
      .matchPattern(matchPattern)
      .count(count)
      .build()
      .asInstanceOf[glide.api.models.commands.scan.ScanOptions]
    serverCmd("SCAN")(
      _.scan(glide.api.models.GlideString.of(cursor), opts).futureLift
        .map(parseScanResult),
      _ =>
        Async[F].raiseError(
          new UnsupportedOperationException(
            "Cluster SCAN with string cursor is not supported; use standalone mode"
          )
        )
    )
  }

  private def parseClusterScanResult(
      result: Array[Object]
  ): ClusterScanResult[List[K]] = {
    val nextCursor = ClusterScanCursor.Wrapped(
      result(0)
        .asInstanceOf[glide.api.models.commands.scan.ClusterScanCursor]
    )
    val keys =
      result(1)
        .asInstanceOf[Array[Object]]
        .toList
        .map {
          case gs: glide.api.models.GlideString => keyCodec.decode(gs)
          case s: String => keyCodec.decode(glide.api.models.GlideString.gs(s))
          case other =>
            keyCodec.decode(
              glide.api.models.GlideString.gs(other.toString)
            )
        }
    ClusterScanResult(nextCursor, keys)
  }

  override def clusterScan(
      cursor: ClusterScanCursor
  ): F[ValkeyResponse[ClusterScanResult[List[K]]]] =
    serverCmd("SCAN (cluster)")(
      _ =>
        Async[F].raiseError(
          new UnsupportedOperationException(
            "clusterScan is only supported in cluster mode; use scan for standalone"
          )
        ),
      c =>
        c.scanBinary(
          cursor.asInstanceOf[ClusterScanCursor.Wrapped].underlying
        ).futureLift
          .map(parseClusterScanResult)
    )

  override def clusterScan(
      cursor: ClusterScanCursor,
      matchPattern: String,
      count: Long
  ): F[ValkeyResponse[ClusterScanResult[List[K]]]] = {
    val opts = glide.api.models.commands.scan.ScanOptions
      .builder()
      .matchPattern(matchPattern)
      .count(count)
      .build()
      .asInstanceOf[glide.api.models.commands.scan.ScanOptions]
    serverCmd("SCAN (cluster)")(
      _ =>
        Async[F].raiseError(
          new UnsupportedOperationException(
            "clusterScan is only supported in cluster mode; use scan for standalone"
          )
        ),
      c =>
        c.scanBinary(
          cursor.asInstanceOf[ClusterScanCursor.Wrapped].underlying,
          opts
        ).futureLift
          .map(parseClusterScanResult)
    )
  }

  // ==================== Hash Commands ====================

  override def hset(key: K, fieldValues: Map[K, V]): F[ValkeyResponse[Long]] = {
    if (fieldValues.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"HSET $key") {
        val javaMap = fieldValues.map { case (k, v) =>
          keyCodec.encode(k) -> valueCodec.encode(v)
        }.asJava
        baseClient
          .hset(keyCodec.encode(key), javaMap)
          .futureLift
          .map(_.longValue())
      }
  }

  override def hget(key: K, field: K): F[ValkeyResponse[Option[V]]] =
    exec(s"HGET $key $field") {
      baseClient
        .hget(keyCodec.encode(key), keyCodec.encode(field))
        .futureLift
        .map(optDecodeV)
    }

  override def hgetall(key: K): F[ValkeyResponse[Map[K, V]]] =
    exec(s"HGETALL $key") {
      baseClient
        .hgetall(keyCodec.encode(key))
        .futureLift
        .map(
          _.asScala
            .map { case (k, v) =>
              keyCodec.decode(k) -> valueCodec.decode(v)
            }
            .toMap
        )
    }

  override def hmget(key: K, fields: K*): F[ValkeyResponse[List[Option[V]]]] = {
    if (fields.isEmpty) Async[F].pure(ValkeyResponse.ok(List.empty))
    else
      exec(s"HMGET $key") {
        baseClient
          .hmget(keyCodec.encode(key), fields.map(keyCodec.encode).toArray)
          .futureLift
          .map(_.toList.map(optDecodeV))
      }
  }

  override def hdel(key: K, fields: K*): F[ValkeyResponse[Long]] = {
    if (fields.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"HDEL $key") {
        baseClient
          .hdel(keyCodec.encode(key), fields.map(keyCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def hexists(key: K, field: K): F[ValkeyResponse[Boolean]] =
    exec(s"HEXISTS $key $field") {
      baseClient
        .hexists(keyCodec.encode(key), keyCodec.encode(field))
        .futureLift
        .map(_.booleanValue())
    }

  override def hkeys(key: K): F[ValkeyResponse[List[K]]] = exec(s"HKEYS $key") {
    baseClient
      .hkeys(keyCodec.encode(key))
      .futureLift
      .map(_.toList.map(keyCodec.decode))
  }

  override def hvals(key: K): F[ValkeyResponse[List[V]]] = exec(s"HVALS $key") {
    baseClient
      .hvals(keyCodec.encode(key))
      .futureLift
      .map(_.toList.map(valueCodec.decode))
  }

  override def hlen(key: K): F[ValkeyResponse[Long]] = exec(s"HLEN $key") {
    baseClient.hlen(keyCodec.encode(key)).futureLift.map(_.longValue())
  }

  override def hincrBy(
      key: K,
      field: K,
      increment: Long
  ): F[ValkeyResponse[Long]] =
    exec(s"HINCRBY $key $field $increment") {
      baseClient
        .hincrBy(keyCodec.encode(key), keyCodec.encode(field), increment)
        .futureLift
        .map(_.longValue())
    }

  override def hincrByFloat(
      key: K,
      field: K,
      increment: Double
  ): F[ValkeyResponse[Double]] =
    exec(s"HINCRBYFLOAT $key $field $increment") {
      baseClient
        .hincrByFloat(keyCodec.encode(key), keyCodec.encode(field), increment)
        .futureLift
        .map(_.doubleValue())
    }

  override def hsetnx(key: K, field: K, value: V): F[ValkeyResponse[Boolean]] =
    exec(s"HSETNX $key $field") {
      baseClient
        .hsetnx(
          keyCodec.encode(key),
          keyCodec.encode(field),
          valueCodec.encode(value)
        )
        .futureLift
        .map(_.booleanValue())
    }

  override def hstrlen(key: K, field: K): F[ValkeyResponse[Long]] =
    exec(s"HSTRLEN $key $field") {
      baseClient
        .hstrlen(keyCodec.encode(key), keyCodec.encode(field))
        .futureLift
        .map(_.longValue())
    }

  override def hrandfield(key: K): F[ValkeyResponse[Option[K]]] =
    exec(s"HRANDFIELD $key") {
      baseClient
        .hrandfield(keyCodec.encode(key))
        .futureLift
        .map(optDecodeK)
    }

  override def hrandfieldWithCount(
      key: K,
      count: Long
  ): F[ValkeyResponse[List[K]]] =
    exec(s"HRANDFIELDWITHCOUNT $key $count") {
      baseClient
        .hrandfieldWithCount(keyCodec.encode(key), count)
        .futureLift
        .map(_.toList.map(keyCodec.decode))
    }

  override def hrandfieldWithCountWithValues(
      key: K,
      count: Long
  ): F[ValkeyResponse[List[(K, V)]]] =
    exec(s"HRANDFIELDWITHCOUNTWITHVALUES $key $count") {
      baseClient
        .hrandfieldWithCountWithValues(keyCodec.encode(key), count)
        .futureLift
        .map(
          _.toList
            .map(pair => keyCodec.decode(pair(0)) -> valueCodec.decode(pair(1)))
        )
    }

  override def hscan(
      key: K,
      cursor: String
  ): F[ValkeyResponse[ScanResult[List[(K, V)]]]] =
    exec("HSCAN") {
      baseClient
        .hscan(
          keyCodec.encode(key),
          glide.api.models.GlideString.of(cursor)
        )
        .futureLift
        .map { result =>
          val nextCursor =
            result(0).asInstanceOf[glide.api.models.GlideString].getString
          val data =
            result(1)
              .asInstanceOf[Array[Object]]
              .map(_.asInstanceOf[glide.api.models.GlideString])
          val pairs = data
            .grouped(2)
            .collect { case Array(f, v) =>
              (keyCodec.decode(f), valueCodec.decode(v))
            }
            .toList
          ScanResult(nextCursor, pairs)
        }
    }

  // ==================== Hash Field Expiration ====================

  private def hashFieldExpConditionOptions(
      condition: ExpireCondition
  ): glide.api.models.commands.HashFieldExpirationConditionOptions =
    glide.api.models.commands.HashFieldExpirationConditionOptions
      .builder()
      .condition(condition.toGlide)
      .build()

  override def hexpire(
      key: K,
      seconds: Long,
      fields: K*
  ): F[ValkeyResponse[List[Long]]] =
    exec("HEXPIRE") {
      baseClient
        .hexpire(
          keyCodec.encode(key),
          seconds,
          fields.map(keyCodec.encode).toArray,
          glide.api.models.commands.HashFieldExpirationConditionOptions
            .builder()
            .build()
        )
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hexpire(
      key: K,
      seconds: Long,
      condition: ExpireCondition,
      fields: K*
  ): F[ValkeyResponse[List[Long]]] =
    exec("HEXPIRE") {
      baseClient
        .hexpire(
          keyCodec.encode(key),
          seconds,
          fields.map(keyCodec.encode).toArray,
          hashFieldExpConditionOptions(condition)
        )
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hpexpire(
      key: K,
      milliseconds: Long,
      fields: K*
  ): F[ValkeyResponse[List[Long]]] =
    exec("HPEXPIRE") {
      baseClient
        .hpexpire(
          keyCodec.encode(key),
          milliseconds,
          fields.map(keyCodec.encode).toArray,
          glide.api.models.commands.HashFieldExpirationConditionOptions
            .builder()
            .build()
        )
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hpexpire(
      key: K,
      milliseconds: Long,
      condition: ExpireCondition,
      fields: K*
  ): F[ValkeyResponse[List[Long]]] =
    exec("HPEXPIRE") {
      baseClient
        .hpexpire(
          keyCodec.encode(key),
          milliseconds,
          fields.map(keyCodec.encode).toArray,
          hashFieldExpConditionOptions(condition)
        )
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hexpireAt(
      key: K,
      unixSeconds: Long,
      fields: K*
  ): F[ValkeyResponse[List[Long]]] =
    exec("HEXPIREAT") {
      baseClient
        .hexpireat(
          keyCodec.encode(key),
          unixSeconds,
          fields.map(keyCodec.encode).toArray,
          glide.api.models.commands.HashFieldExpirationConditionOptions
            .builder()
            .build()
        )
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hexpireAt(
      key: K,
      unixSeconds: Long,
      condition: ExpireCondition,
      fields: K*
  ): F[ValkeyResponse[List[Long]]] =
    exec("HEXPIREAT") {
      baseClient
        .hexpireat(
          keyCodec.encode(key),
          unixSeconds,
          fields.map(keyCodec.encode).toArray,
          hashFieldExpConditionOptions(condition)
        )
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hpexpireAt(
      key: K,
      unixMilliseconds: Long,
      fields: K*
  ): F[ValkeyResponse[List[Long]]] =
    exec("HPEXPIREAT") {
      baseClient
        .hpexpireat(
          keyCodec.encode(key),
          unixMilliseconds,
          fields.map(keyCodec.encode).toArray,
          glide.api.models.commands.HashFieldExpirationConditionOptions
            .builder()
            .build()
        )
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hpexpireAt(
      key: K,
      unixMilliseconds: Long,
      condition: ExpireCondition,
      fields: K*
  ): F[ValkeyResponse[List[Long]]] =
    exec("HPEXPIREAT") {
      baseClient
        .hpexpireat(
          keyCodec.encode(key),
          unixMilliseconds,
          fields.map(keyCodec.encode).toArray,
          hashFieldExpConditionOptions(condition)
        )
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def httl(key: K, fields: K*): F[ValkeyResponse[List[Long]]] =
    exec("HTTL") {
      baseClient
        .httl(keyCodec.encode(key), fields.map(keyCodec.encode).toArray)
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hpttl(key: K, fields: K*): F[ValkeyResponse[List[Long]]] =
    exec("HPTTL") {
      baseClient
        .hpttl(keyCodec.encode(key), fields.map(keyCodec.encode).toArray)
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hexpireTime(
      key: K,
      fields: K*
  ): F[ValkeyResponse[List[Long]]] =
    exec("HEXPIRETIME") {
      baseClient
        .hexpiretime(keyCodec.encode(key), fields.map(keyCodec.encode).toArray)
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hpexpireTime(
      key: K,
      fields: K*
  ): F[ValkeyResponse[List[Long]]] =
    exec("HPEXPIRETIME") {
      baseClient
        .hpexpiretime(keyCodec.encode(key), fields.map(keyCodec.encode).toArray)
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hpersist(key: K, fields: K*): F[ValkeyResponse[List[Long]]] =
    exec("HPERSIST") {
      baseClient
        .hpersist(keyCodec.encode(key), fields.map(keyCodec.encode).toArray)
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def hgetex(
      key: K,
      expiry: HGetExExpiry,
      fields: K*
  ): F[ValkeyResponse[List[Option[V]]]] =
    exec("HGETEX") {
      baseClient
        .hgetex(
          keyCodec.encode(key),
          fields.map(keyCodec.encode).toArray,
          expiry.toGlide
        )
        .futureLift
        .map(_.toList.map(optDecodeV))
    }

  override def hsetex(
      key: K,
      fieldValues: Map[K, V],
      expiry: ExpirySet
  ): F[ValkeyResponse[Long]] = {
    if (fieldValues.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec("HSETEX") {
        val javaMap = fieldValues.map { case (k, v) =>
          keyCodec.encode(k) -> valueCodec.encode(v)
        }.asJava
        val opts = glide.api.models.commands.HSetExOptions
          .builder()
          .expiry(expiry.toGlide)
          .build()
        baseClient
          .hsetex(keyCodec.encode(key), javaMap, opts)
          .futureLift
          .map(_.longValue())
      }
  }

  override def hsetex(
      key: K,
      fieldValues: Map[K, V],
      expiry: ExpirySet,
      condition: FieldCondition
  ): F[ValkeyResponse[Long]] = {
    if (fieldValues.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec("HSETEX") {
        val javaMap = fieldValues.map { case (k, v) =>
          keyCodec.encode(k) -> valueCodec.encode(v)
        }.asJava
        val opts = glide.api.models.commands.HSetExOptions
          .builder()
          .expiry(expiry.toGlide)
          .fieldConditionalChange(condition.toGlide)
          .build()
        baseClient
          .hsetex(keyCodec.encode(key), javaMap, opts)
          .futureLift
          .map(_.longValue())
      }
  }

  // ==================== List Commands ====================

  override def lpush(key: K, elements: V*): F[ValkeyResponse[Long]] = {
    if (elements.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"LPUSH $key") {
        baseClient
          .lpush(keyCodec.encode(key), elements.map(valueCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def rpush(key: K, elements: V*): F[ValkeyResponse[Long]] = {
    if (elements.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"RPUSH $key") {
        baseClient
          .rpush(keyCodec.encode(key), elements.map(valueCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def lpop(key: K): F[ValkeyResponse[Option[V]]] = exec(s"LPOP $key") {
    baseClient
      .lpop(keyCodec.encode(key))
      .futureLift
      .map(optDecodeV)
  }

  override def rpop(key: K): F[ValkeyResponse[Option[V]]] = exec(s"RPOP $key") {
    baseClient
      .rpop(keyCodec.encode(key))
      .futureLift
      .map(optDecodeV)
  }

  override def lpopCount(key: K, count: Long): F[ValkeyResponse[List[V]]] =
    exec(s"LPOPCOUNT $key $count") {
      baseClient
        .lpopCount(keyCodec.encode(key), count)
        .futureLift
        .map(arr =>
          if (arr == null) List.empty else arr.toList.map(valueCodec.decode)
        )
    }

  override def rpopCount(key: K, count: Long): F[ValkeyResponse[List[V]]] =
    exec(s"RPOPCOUNT $key $count") {
      baseClient
        .rpopCount(keyCodec.encode(key), count)
        .futureLift
        .map(arr =>
          if (arr == null) List.empty else arr.toList.map(valueCodec.decode)
        )
    }

  override def lrange(
      key: K,
      start: Long,
      stop: Long
  ): F[ValkeyResponse[List[V]]] =
    exec(s"LRANGE $key $start $stop") {
      baseClient
        .lrange(keyCodec.encode(key), start, stop)
        .futureLift
        .map(_.toList.map(valueCodec.decode))
    }

  override def lindex(key: K, index: Long): F[ValkeyResponse[Option[V]]] =
    exec(s"LINDEX $key $index") {
      baseClient
        .lindex(keyCodec.encode(key), index)
        .futureLift
        .map(optDecodeV)
    }

  override def llen(key: K): F[ValkeyResponse[Long]] = exec(s"LLEN $key") {
    baseClient.llen(keyCodec.encode(key)).futureLift.map(_.longValue())
  }

  override def ltrim(key: K, start: Long, stop: Long): F[ValkeyResponse[Unit]] =
    exec(s"LTRIM $key $start $stop") {
      baseClient.ltrim(keyCodec.encode(key), start, stop).futureLift.void
    }

  override def lset(key: K, index: Long, element: V): F[ValkeyResponse[Unit]] =
    exec(s"LSET $key $index") {
      baseClient
        .lset(keyCodec.encode(key), index, valueCodec.encode(element))
        .futureLift
        .void
    }

  override def lrem(key: K, count: Long, element: V): F[ValkeyResponse[Long]] =
    exec(s"LREM $key $count") {
      baseClient
        .lrem(keyCodec.encode(key), count, valueCodec.encode(element))
        .futureLift
        .map(_.longValue())
    }

  override def linsert(
      key: K,
      position: InsertPosition,
      pivot: V,
      element: V
  ): F[ValkeyResponse[InsertResult]] = exec(s"LINSERT $key") {
    baseClient
      .linsert(
        keyCodec.encode(key),
        position.toGlide,
        valueCodec.encode(pivot),
        valueCodec.encode(element)
      )
      .futureLift
      .map { raw =>
        val n = raw.longValue()
        if (n == -1L) InsertResult.PivotNotFound
        else InsertResult.Inserted(n)
      }
  }

  override def lpos(key: K, element: V): F[ValkeyResponse[Option[Long]]] =
    exec(s"LPOS $key") {
      baseClient
        .lpos(keyCodec.encode(key), valueCodec.encode(element))
        .futureLift
        .map(result => Option(result).map(_.longValue()))
    }

  override def lpushx(key: K, elements: V*): F[ValkeyResponse[Long]] = {
    if (elements.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"LPUSHX $key") {
        baseClient
          .lpushx(
            keyCodec.encode(key),
            elements.map(valueCodec.encode).toArray
          )
          .futureLift
          .map(_.longValue())
      }
  }

  override def rpushx(key: K, elements: V*): F[ValkeyResponse[Long]] = {
    if (elements.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"RPUSHX $key") {
        baseClient
          .rpushx(
            keyCodec.encode(key),
            elements.map(valueCodec.encode).toArray
          )
          .futureLift
          .map(_.longValue())
      }
  }

  override def lmove(
      source: K,
      destination: K,
      from: arguments.ListDirection,
      to: arguments.ListDirection
  ): F[ValkeyResponse[Option[V]]] =
    exec(s"LMOVE $source -> $destination") {
      baseClient
        .lmove(
          keyCodec.encode(source),
          keyCodec.encode(destination),
          from.toGlide,
          to.toGlide
        )
        .futureLift
        .map(optDecodeV)
    }

  override def blpop(
      keys: List[K],
      timeout: Double
  ): F[ValkeyResponse[Option[(K, V)]]] =
    exec("BLPOP") {
      val keysArray = keys.map(keyCodec.encode).toArray
      baseClient
        .blpop(keysArray, timeout)
        .futureLift
        .map { arr =>
          if (arr == null || arr.length < 2) None
          else Some((keyCodec.decode(arr(0)), valueCodec.decode(arr(1))))
        }
    }

  override def brpop(
      keys: List[K],
      timeout: Double
  ): F[ValkeyResponse[Option[(K, V)]]] =
    exec("BRPOP") {
      val keysArray = keys.map(keyCodec.encode).toArray
      baseClient
        .brpop(keysArray, timeout)
        .futureLift
        .map { arr =>
          if (arr == null || arr.length < 2) None
          else Some((keyCodec.decode(arr(0)), valueCodec.decode(arr(1))))
        }
    }

  override def lposCount(
      key: K,
      element: V,
      count: Long
  ): F[ValkeyResponse[List[Long]]] =
    exec(s"LPOS (count) $key") {
      baseClient
        .lposCount(keyCodec.encode(key), valueCodec.encode(element), count)
        .futureLift
        .map(_.toList.map(_.longValue()))
    }

  override def blmove(
      source: K,
      destination: K,
      from: arguments.ListDirection,
      to: arguments.ListDirection,
      timeout: Double
  ): F[ValkeyResponse[Option[V]]] =
    exec("BLMOVE") {
      baseClient
        .blmove(
          keyCodec.encode(source),
          keyCodec.encode(destination),
          from.toGlide,
          to.toGlide,
          timeout
        )
        .futureLift
        .map(optDecodeV)
    }

  private def parseLmpopResult(
      result: java.util.Map[
        glide.api.models.GlideString,
        Array[glide.api.models.GlideString]
      ]
  ): Option[(K, List[V])] =
    if (result == null || result.isEmpty) None
    else {
      val entry = result.asScala.head
      Some(
        (
          keyCodec.decode(entry._1),
          entry._2.toList.map(valueCodec.decode)
        )
      )
    }

  override def lmpop(
      keys: List[K],
      direction: ListDirection
  ): F[ValkeyResponse[Option[(K, List[V])]]] =
    exec("LMPOP") {
      baseClient
        .lmpop(keys.map(keyCodec.encode).toArray, direction.toGlide)
        .futureLift
        .map(parseLmpopResult)
    }

  override def lmpop(
      keys: List[K],
      direction: ListDirection,
      count: Long
  ): F[ValkeyResponse[Option[(K, List[V])]]] =
    exec("LMPOP") {
      baseClient
        .lmpop(keys.map(keyCodec.encode).toArray, direction.toGlide, count)
        .futureLift
        .map(parseLmpopResult)
    }

  override def blmpop(
      keys: List[K],
      direction: ListDirection,
      timeout: Double
  ): F[ValkeyResponse[Option[(K, List[V])]]] =
    exec("BLMPOP") {
      baseClient
        .blmpop(keys.map(keyCodec.encode).toArray, direction.toGlide, timeout)
        .futureLift
        .map(parseLmpopResult)
    }

  override def blmpop(
      keys: List[K],
      direction: ListDirection,
      count: Long,
      timeout: Double
  ): F[ValkeyResponse[Option[(K, List[V])]]] =
    exec("BLMPOP") {
      baseClient
        .blmpop(
          keys.map(keyCodec.encode).toArray,
          direction.toGlide,
          count,
          timeout
        )
        .futureLift
        .map(parseLmpopResult)
    }

  // ==================== Set Commands ====================

  override def sadd(key: K, members: V*): F[ValkeyResponse[Long]] = {
    if (members.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"SADD $key") {
        baseClient
          .sadd(keyCodec.encode(key), members.map(valueCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def srem(key: K, members: V*): F[ValkeyResponse[Long]] = {
    if (members.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"SREM $key") {
        baseClient
          .srem(keyCodec.encode(key), members.map(valueCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def smembers(key: K): F[ValkeyResponse[Set[V]]] =
    exec(s"SMEMBERS $key") {
      baseClient
        .smembers(keyCodec.encode(key))
        .futureLift
        .map(_.asScala.map(valueCodec.decode).toSet)
    }

  override def sismember(key: K, member: V): F[ValkeyResponse[Boolean]] =
    exec(s"SISMEMBER $key") {
      baseClient
        .sismember(keyCodec.encode(key), valueCodec.encode(member))
        .futureLift
        .map(_.booleanValue())
    }

  override def smismember(
      key: K,
      members: V*
  ): F[ValkeyResponse[List[Boolean]]] = {
    if (members.isEmpty) Async[F].pure(ValkeyResponse.ok(List.empty[Boolean]))
    else
      exec(s"SMISMEMBER $key") {
        baseClient
          .smismember(
            keyCodec.encode(key),
            members.map(valueCodec.encode).toArray
          )
          .futureLift
          .map(_.map(_.booleanValue()).toList)
      }
  }

  override def scard(key: K): F[ValkeyResponse[Long]] = exec(s"SCARD $key") {
    baseClient.scard(keyCodec.encode(key)).futureLift.map(_.longValue())
  }

  override def sunion(keys: K*): F[ValkeyResponse[Set[V]]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(Set.empty[V]))
    else
      exec("SUNION") {
        baseClient
          .sunion(keys.map(keyCodec.encode).toArray)
          .futureLift
          .map(_.asScala.map(valueCodec.decode).toSet)
      }
  }

  override def sunionstore(
      destination: K,
      keys: K*
  ): F[ValkeyResponse[Long]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"SUNIONSTORE $destination") {
        baseClient
          .sunionstore(
            keyCodec.encode(destination),
            keys.map(keyCodec.encode).toArray
          )
          .futureLift
          .map(_.longValue())
      }
  }

  override def sinter(keys: K*): F[ValkeyResponse[Set[V]]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(Set.empty[V]))
    else
      exec("SINTER") {
        baseClient
          .sinter(keys.map(keyCodec.encode).toArray)
          .futureLift
          .map(_.asScala.map(valueCodec.decode).toSet)
      }
  }

  override def sinterstore(
      destination: K,
      keys: K*
  ): F[ValkeyResponse[Long]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"SINTERSTORE $destination") {
        baseClient
          .sinterstore(
            keyCodec.encode(destination),
            keys.map(keyCodec.encode).toArray
          )
          .futureLift
          .map(_.longValue())
      }
  }

  override def sdiff(keys: K*): F[ValkeyResponse[Set[V]]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(Set.empty[V]))
    else
      exec("SDIFF") {
        baseClient
          .sdiff(keys.map(keyCodec.encode).toArray)
          .futureLift
          .map(_.asScala.map(valueCodec.decode).toSet)
      }
  }

  override def sdiffstore(destination: K, keys: K*): F[ValkeyResponse[Long]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"SDIFFSTORE $destination") {
        baseClient
          .sdiffstore(
            keyCodec.encode(destination),
            keys.map(keyCodec.encode).toArray
          )
          .futureLift
          .map(_.longValue())
      }
  }

  override def spop(key: K): F[ValkeyResponse[Option[V]]] = exec(s"SPOP $key") {
    baseClient
      .spop(keyCodec.encode(key))
      .futureLift
      .map(optDecodeV)
  }

  override def spopCount(key: K, count: Long): F[ValkeyResponse[Set[V]]] =
    exec(s"SPOP $key $count") {
      baseClient
        .spopCount(keyCodec.encode(key), count)
        .futureLift
        .map(_.asScala.map(valueCodec.decode).toSet)
    }

  override def srandmember(key: K): F[ValkeyResponse[Option[V]]] =
    exec(s"SRANDMEMBER $key") {
      baseClient
        .srandmember(keyCodec.encode(key))
        .futureLift
        .map(optDecodeV)
    }

  override def srandmemberCount(
      key: K,
      count: Long
  ): F[ValkeyResponse[List[V]]] =
    exec(s"SRANDMEMBER $key $count") {
      baseClient
        .srandmember(keyCodec.encode(key), count)
        .futureLift
        .map(_.toList.map(valueCodec.decode))
    }

  override def smove(
      source: K,
      destination: K,
      member: V
  ): F[ValkeyResponse[Boolean]] =
    exec(s"SMOVE $source $destination") {
      baseClient
        .smove(
          keyCodec.encode(source),
          keyCodec.encode(destination),
          valueCodec.encode(member)
        )
        .futureLift
        .map(_.booleanValue())
    }

  override def sintercard(keys: K*): F[ValkeyResponse[Long]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec("SINTERCARD") {
        baseClient
          .sintercard(keys.map(keyCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def sintercard(limit: Long, keys: K*): F[ValkeyResponse[Long]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec("SINTERCARD") {
        baseClient
          .sintercard(keys.map(keyCodec.encode).toArray, limit)
          .futureLift
          .map(_.longValue())
      }
  }

  override def sscan(
      key: K,
      cursor: String
  ): F[ValkeyResponse[ScanResult[Set[V]]]] =
    exec("SSCAN") {
      baseClient
        .sscan(
          keyCodec.encode(key),
          glide.api.models.GlideString.of(cursor)
        )
        .futureLift
        .map { result =>
          val nextCursor =
            result(0).asInstanceOf[glide.api.models.GlideString].getString
          val data =
            result(1)
              .asInstanceOf[Array[Object]]
              .map(_.asInstanceOf[glide.api.models.GlideString])
          ScanResult(nextCursor, data.toList.map(valueCodec.decode).toSet)
        }
    }

  // ==================== Sorted Set Commands ====================

  override def zadd(
      key: K,
      membersScores: Map[V, Double]
  ): F[ValkeyResponse[Long]] = {
    if (membersScores.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"ZADD $key") {
        val javaMap = membersScores.map { case (member, score) =>
          valueCodec.encode(member) -> Double.box(score)
        }.asJava
        baseClient
          .zadd(keyCodec.encode(key), javaMap)
          .futureLift
          .map(_.longValue())
      }
  }

  override def zadd(
      key: K,
      membersScores: Map[V, Double],
      options: ZAddOptions
  ): F[ValkeyResponse[Long]] = {
    if (membersScores.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"ZADD $key with options") {
        val javaMap = membersScores.map { case (member, score) =>
          valueCodec.encode(member) -> Double.box(score)
        }.asJava
        baseClient
          .zadd(keyCodec.encode(key), javaMap, ZAddOptions.toGlide(options))
          .futureLift
          .map(_.longValue())
      }
  }

  override def zaddIncr(
      key: K,
      member: V,
      score: Double
  ): F[ValkeyResponse[Option[Double]]] =
    exec(s"ZADD INCR $key") {
      baseClient
        .zaddIncr(keyCodec.encode(key), valueCodec.encode(member), score)
        .futureLift
        .map(d => Option(d).map(_.doubleValue()))
    }

  override def zrem(key: K, members: V*): F[ValkeyResponse[Long]] = {
    if (members.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec(s"ZREM $key") {
        baseClient
          .zrem(keyCodec.encode(key), members.map(valueCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def zrange(
      key: K,
      start: Long,
      stop: Long
  ): F[ValkeyResponse[List[V]]] =
    exec(s"ZRANGE $key $start $stop") {
      val rangeQuery =
        new glide.api.models.commands.RangeOptions.RangeByIndex(start, stop)
      baseClient
        .zrange(keyCodec.encode(key), rangeQuery)
        .futureLift
        .map(_.toList.map(valueCodec.decode))
    }

  override def zrangeWithScores(
      key: K,
      start: Long,
      stop: Long
  ): F[ValkeyResponse[List[(V, Double)]]] =
    exec(s"ZRANGE (with scores) $key $start $stop") {
      val rangeQuery =
        new glide.api.models.commands.RangeOptions.RangeByIndex(start, stop)
      baseClient
        .zrangeWithScores(keyCodec.encode(key), rangeQuery)
        .futureLift
        .map(_.asScala.toList.map { case (gs, score) =>
          (valueCodec.decode(gs), score.doubleValue())
        })
    }

  override def zscore(key: K, member: V): F[ValkeyResponse[Option[Double]]] =
    exec(s"ZSCORE $key") {
      baseClient
        .zscore(keyCodec.encode(key), valueCodec.encode(member))
        .futureLift
        .map(result => Option(result).map(_.doubleValue()))
    }

  override def zmscore(
      key: K,
      members: V*
  ): F[ValkeyResponse[List[Option[Double]]]] = {
    if (members.isEmpty)
      Async[F].pure(ValkeyResponse.ok(List.empty[Option[Double]]))
    else
      exec(s"ZMSCORE $key") {
        baseClient
          .zmscore(keyCodec.encode(key), members.map(valueCodec.encode).toArray)
          .futureLift
          .map(_.toList.map(score => Option(score).map(_.doubleValue())))
      }
  }

  override def zcard(key: K): F[ValkeyResponse[Long]] = exec(s"ZCARD $key") {
    baseClient.zcard(keyCodec.encode(key)).futureLift.map(_.longValue())
  }

  override def zrank(key: K, member: V): F[ValkeyResponse[Option[Long]]] =
    exec(s"ZRANK $key") {
      baseClient
        .zrank(keyCodec.encode(key), valueCodec.encode(member))
        .futureLift
        .map(result => Option(result).map(_.longValue()))
    }

  override def zrevrank(key: K, member: V): F[ValkeyResponse[Option[Long]]] =
    exec(s"ZREVRANK $key") {
      baseClient
        .zrevrank(keyCodec.encode(key), valueCodec.encode(member))
        .futureLift
        .map(result => Option(result).map(_.longValue()))
    }

  override def zincrby(
      key: K,
      increment: Double,
      member: V
  ): F[ValkeyResponse[Double]] =
    exec(s"ZINCRBY $key $increment") {
      baseClient
        .zincrby(keyCodec.encode(key), increment, valueCodec.encode(member))
        .futureLift
        .map(_.doubleValue())
    }

  override def zcount(
      key: K,
      min: Double,
      max: Double
  ): F[ValkeyResponse[Long]] =
    exec(s"ZCOUNT $key $min $max") {
      val minScore =
        new glide.api.models.commands.RangeOptions.ScoreBoundary(min, true)
      val maxScore =
        new glide.api.models.commands.RangeOptions.ScoreBoundary(max, true)
      baseClient
        .zcount(keyCodec.encode(key), minScore, maxScore)
        .futureLift
        .map(_.longValue())
    }

  private def decodeScoreMap(
      javaMap: java.util.Map[glide.api.models.GlideString, java.lang.Double]
  ): List[(V, Double)] =
    javaMap.asScala.toList.map { case (gs, score) =>
      (valueCodec.decode(gs), score.doubleValue())
    }

  override def zpopmin(key: K): F[ValkeyResponse[Option[(V, Double)]]] =
    exec(s"ZPOPMIN $key") {
      baseClient
        .zpopmin(keyCodec.encode(key))
        .futureLift
        .map(_.asScala.headOption.map { case (gs, score) =>
          (valueCodec.decode(gs), score.doubleValue())
        })
    }

  override def zpopminCount(
      key: K,
      count: Long
  ): F[ValkeyResponse[List[(V, Double)]]] =
    exec(s"ZPOPMIN $key $count") {
      baseClient
        .zpopmin(keyCodec.encode(key), count)
        .futureLift
        .map(decodeScoreMap)
    }

  override def zpopmax(key: K): F[ValkeyResponse[Option[(V, Double)]]] =
    exec(s"ZPOPMAX $key") {
      baseClient
        .zpopmax(keyCodec.encode(key))
        .futureLift
        .map(_.asScala.headOption.map { case (gs, score) =>
          (valueCodec.decode(gs), score.doubleValue())
        })
    }

  override def zpopmaxCount(
      key: K,
      count: Long
  ): F[ValkeyResponse[List[(V, Double)]]] =
    exec(s"ZPOPMAX $key $count") {
      baseClient
        .zpopmax(keyCodec.encode(key), count)
        .futureLift
        .map(decodeScoreMap)
    }

  override def zrandmember(key: K): F[ValkeyResponse[Option[V]]] =
    exec(s"ZRANDMEMBER $key") {
      baseClient
        .zrandmember(keyCodec.encode(key))
        .futureLift
        .map(optDecodeV)
    }

  override def zrandmemberCount(
      key: K,
      count: Long
  ): F[ValkeyResponse[List[V]]] =
    exec(s"ZRANDMEMBER $key $count") {
      baseClient
        .zrandmemberWithCount(keyCodec.encode(key), count)
        .futureLift
        .map(_.toList.map(valueCodec.decode))
    }

  override def zrandmemberWithScores(
      key: K,
      count: Long
  ): F[ValkeyResponse[List[(V, Double)]]] =
    exec(s"ZRANDMEMBER (with scores) $key $count") {
      baseClient
        .zrandmemberWithCountWithScores(keyCodec.encode(key), count)
        .futureLift
        .map(_.toList.map { pair =>
          val gs = pair(0).asInstanceOf[glide.api.models.GlideString]
          val score = pair(1).asInstanceOf[java.lang.Double]
          (valueCodec.decode(gs), score.doubleValue())
        })
    }

  override def zremrangebyrank(
      key: K,
      start: Long,
      stop: Long
  ): F[ValkeyResponse[Long]] =
    exec(s"ZREMRANGEBYRANK $key") {
      baseClient
        .zremrangebyrank(keyCodec.encode(key), start, stop)
        .futureLift
        .map(_.longValue())
    }

  override def zremrangebyscore(
      key: K,
      min: arguments.ScoreBoundary,
      max: arguments.ScoreBoundary
  ): F[ValkeyResponse[Long]] =
    exec(s"ZREMRANGEBYSCORE $key") {
      baseClient
        .zremrangebyscore(
          keyCodec.encode(key),
          min.toGlide,
          max.toGlide
        )
        .futureLift
        .map(_.longValue())
    }

  override def zdiff(keys: K*): F[ValkeyResponse[List[V]]] =
    exec("ZDIFF") {
      val keysArray = keys.map(keyCodec.encode).toArray
      baseClient
        .zdiff(keysArray)
        .futureLift
        .map(_.toList.map(valueCodec.decode))
    }

  override def zdiffstore(
      destination: K,
      keys: K*
  ): F[ValkeyResponse[Long]] =
    exec(s"ZDIFFSTORE $destination") {
      val keysArray = keys.map(keyCodec.encode).toArray
      baseClient
        .zdiffstore(keyCodec.encode(destination), keysArray)
        .futureLift
        .map(_.longValue())
    }

  override def zunion(keys: K*): F[ValkeyResponse[List[V]]] =
    exec("ZUNION") {
      val keysArray = keys.map(keyCodec.encode).toArray
      val keyArray =
        new glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary(
          keysArray
        )
      baseClient
        .zunion(keyArray)
        .futureLift
        .map(_.toList.map(valueCodec.decode))
    }

  override def zunionstore(
      destination: K,
      keys: K*
  ): F[ValkeyResponse[Long]] =
    exec(s"ZUNIONSTORE $destination") {
      val keysArray = keys.map(keyCodec.encode).toArray
      val keyArray =
        new glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary(
          keysArray
        )
      baseClient
        .zunionstore(keyCodec.encode(destination), keyArray)
        .futureLift
        .map(_.longValue())
    }

  override def zinter(keys: K*): F[ValkeyResponse[List[V]]] =
    exec("ZINTER") {
      val keysArray = keys.map(keyCodec.encode).toArray
      val keyArray =
        new glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary(
          keysArray
        )
      baseClient
        .zinter(keyArray)
        .futureLift
        .map(_.toList.map(valueCodec.decode))
    }

  override def zinterstore(
      destination: K,
      keys: K*
  ): F[ValkeyResponse[Long]] =
    exec(s"ZINTERSTORE $destination") {
      val keysArray = keys.map(keyCodec.encode).toArray
      val keyArray =
        new glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary(
          keysArray
        )
      baseClient
        .zinterstore(keyCodec.encode(destination), keyArray)
        .futureLift
        .map(_.longValue())
    }

  override def zintercard(keys: K*): F[ValkeyResponse[Long]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec("ZINTERCARD") {
        baseClient
          .zintercard(keys.map(keyCodec.encode).toArray)
          .futureLift
          .map(_.longValue())
      }
  }

  override def zintercard(limit: Long, keys: K*): F[ValkeyResponse[Long]] = {
    if (keys.isEmpty) Async[F].pure(ValkeyResponse.ok(0L))
    else
      exec("ZINTERCARD") {
        baseClient
          .zintercard(keys.map(keyCodec.encode).toArray, limit)
          .futureLift
          .map(_.longValue())
      }
  }

  override def zrankWithScore(
      key: K,
      member: V
  ): F[ValkeyResponse[Option[(Long, Double)]]] =
    exec(s"ZRANK (with score) $key") {
      baseClient
        .zrankWithScore(keyCodec.encode(key), valueCodec.encode(member))
        .futureLift
        .map { arr =>
          if (arr == null || arr.length < 2 || arr(0) == null) None
          else
            Some(
              (
                arr(0).asInstanceOf[java.lang.Long].longValue(),
                arr(1).asInstanceOf[java.lang.Double].doubleValue()
              )
            )
        }
    }

  override def zrevrankWithScore(
      key: K,
      member: V
  ): F[ValkeyResponse[Option[(Long, Double)]]] =
    exec(s"ZREVRANK (with score) $key") {
      baseClient
        .zrevrankWithScore(keyCodec.encode(key), valueCodec.encode(member))
        .futureLift
        .map { arr =>
          if (arr == null || arr.length < 2 || arr(0) == null) None
          else
            Some(
              (
                arr(0).asInstanceOf[java.lang.Long].longValue(),
                arr(1).asInstanceOf[java.lang.Double].doubleValue()
              )
            )
        }
    }

  override def bzpopmin(
      keys: List[K],
      timeout: Double
  ): F[ValkeyResponse[Option[(K, V, Double)]]] =
    exec("BZPOPMIN") {
      val keysArray = keys.map(keyCodec.encode).toArray
      baseClient
        .bzpopmin(keysArray, timeout)
        .futureLift
        .map { arr =>
          if (arr == null || arr.length < 3 || arr(0) == null) None
          else
            Some(
              (
                keyCodec.decode(
                  arr(0).asInstanceOf[glide.api.models.GlideString]
                ),
                valueCodec.decode(
                  arr(1).asInstanceOf[glide.api.models.GlideString]
                ),
                arr(2).asInstanceOf[java.lang.Double].doubleValue()
              )
            )
        }
    }

  override def bzpopmax(
      keys: List[K],
      timeout: Double
  ): F[ValkeyResponse[Option[(K, V, Double)]]] =
    exec("BZPOPMAX") {
      val keysArray = keys.map(keyCodec.encode).toArray
      baseClient
        .bzpopmax(keysArray, timeout)
        .futureLift
        .map { arr =>
          if (arr == null || arr.length < 3 || arr(0) == null) None
          else
            Some(
              (
                keyCodec.decode(
                  arr(0).asInstanceOf[glide.api.models.GlideString]
                ),
                valueCodec.decode(
                  arr(1).asInstanceOf[glide.api.models.GlideString]
                ),
                arr(2).asInstanceOf[java.lang.Double].doubleValue()
              )
            )
        }
    }

  override def zdiffWithScores(
      keys: K*
  ): F[ValkeyResponse[List[(V, Double)]]] =
    exec("ZDIFFWITHSCORES") {
      baseClient
        .zdiffWithScores(keys.map(keyCodec.encode).toArray)
        .futureLift
        .map(decodeScoreMap)
    }

  override def zunionWithScores(
      keys: K*
  ): F[ValkeyResponse[List[(V, Double)]]] =
    exec("ZUNIONWITHSCORES") {
      val keyArray =
        new glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary(
          keys.map(keyCodec.encode).toArray
        )
      baseClient
        .zunionWithScores(keyArray)
        .futureLift
        .map(decodeScoreMap)
    }

  override def zunionWithScores(
      keys: List[K],
      aggregate: AggregateOption
  ): F[ValkeyResponse[List[(V, Double)]]] =
    exec("ZUNIONWITHSCORES") {
      val keyArray =
        new glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary(
          keys.map(keyCodec.encode).toArray
        )
      baseClient
        .zunionWithScores(keyArray, aggregate.toGlide)
        .futureLift
        .map(decodeScoreMap)
    }

  override def zinterWithScores(
      keys: K*
  ): F[ValkeyResponse[List[(V, Double)]]] =
    exec("ZINTERWITHSCORES") {
      val keyArray =
        new glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary(
          keys.map(keyCodec.encode).toArray
        )
      baseClient
        .zinterWithScores(keyArray)
        .futureLift
        .map(decodeScoreMap)
    }

  override def zinterWithScores(
      keys: List[K],
      aggregate: AggregateOption
  ): F[ValkeyResponse[List[(V, Double)]]] =
    exec("ZINTERWITHSCORES") {
      val keyArray =
        new glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary(
          keys.map(keyCodec.encode).toArray
        )
      baseClient
        .zinterWithScores(keyArray, aggregate.toGlide)
        .futureLift
        .map(decodeScoreMap)
    }

  override def zlexcount(
      key: K,
      min: LexBoundary,
      max: LexBoundary
  ): F[ValkeyResponse[Long]] =
    exec("ZLEXCOUNT") {
      baseClient
        .zlexcount(keyCodec.encode(key), min.toGlide, max.toGlide)
        .futureLift
        .map(_.longValue())
    }

  override def zremrangebylex(
      key: K,
      min: LexBoundary,
      max: LexBoundary
  ): F[ValkeyResponse[Long]] =
    exec("ZREMRANGEBYLEX") {
      baseClient
        .zremrangebylex(keyCodec.encode(key), min.toGlide, max.toGlide)
        .futureLift
        .map(_.longValue())
    }

  override def zrangestore(
      destination: K,
      source: K,
      rangeQuery: RangeQuery
  ): F[ValkeyResponse[Long]] =
    exec("ZRANGESTORE") {
      baseClient
        .zrangestore(
          keyCodec.encode(destination),
          keyCodec.encode(source),
          rangeQuery.toGlide
        )
        .futureLift
        .map(_.longValue())
    }

  override def zrangestore(
      destination: K,
      source: K,
      rangeQuery: RangeQuery,
      reverse: Boolean
  ): F[ValkeyResponse[Long]] =
    exec("ZRANGESTORE") {
      baseClient
        .zrangestore(
          keyCodec.encode(destination),
          keyCodec.encode(source),
          rangeQuery.toGlide,
          reverse
        )
        .futureLift
        .map(_.longValue())
    }

  @SuppressWarnings(Array("unchecked"))
  private def parseZmpopResult(
      result: java.util.Map[glide.api.models.GlideString, java.lang.Object]
  ): Option[(K, List[(V, Double)])] =
    if (result == null || result.isEmpty) None
    else {
      val entry = result.asScala.head
      val key = keyCodec.decode(entry._1)
      val membersScores = entry._2
        .asInstanceOf[
          java.util.Map[glide.api.models.GlideString, java.lang.Double]
        ]
        .asScala
        .toList
        .map { case (gs, score) =>
          (valueCodec.decode(gs), score.doubleValue())
        }
      Some((key, membersScores))
    }

  override def zmpop(
      keys: List[K],
      filter: ScoreFilter
  ): F[ValkeyResponse[Option[(K, List[(V, Double)])]]] =
    exec("ZMPOP") {
      baseClient
        .zmpop(keys.map(keyCodec.encode).toArray, filter.toGlide)
        .futureLift
        .map(parseZmpopResult)
    }

  override def zmpop(
      keys: List[K],
      filter: ScoreFilter,
      count: Long
  ): F[ValkeyResponse[Option[(K, List[(V, Double)])]]] =
    exec("ZMPOP") {
      baseClient
        .zmpop(keys.map(keyCodec.encode).toArray, filter.toGlide, count)
        .futureLift
        .map(parseZmpopResult)
    }

  override def bzmpop(
      keys: List[K],
      filter: ScoreFilter,
      timeout: Double
  ): F[ValkeyResponse[Option[(K, List[(V, Double)])]]] =
    exec("BZMPOP") {
      baseClient
        .bzmpop(keys.map(keyCodec.encode).toArray, filter.toGlide, timeout)
        .futureLift
        .map(parseZmpopResult)
    }

  override def bzmpop(
      keys: List[K],
      filter: ScoreFilter,
      timeout: Double,
      count: Long
  ): F[ValkeyResponse[Option[(K, List[(V, Double)])]]] =
    exec("BZMPOP") {
      baseClient
        .bzmpop(
          keys.map(keyCodec.encode).toArray,
          filter.toGlide,
          timeout,
          count
        )
        .futureLift
        .map(parseZmpopResult)
    }

  override def zscan(
      key: K,
      cursor: String
  ): F[ValkeyResponse[ScanResult[List[(V, Double)]]]] =
    exec("ZSCAN") {
      baseClient
        .zscan(
          keyCodec.encode(key),
          glide.api.models.GlideString.of(cursor)
        )
        .futureLift
        .map { result =>
          val nextCursor =
            result(0).asInstanceOf[glide.api.models.GlideString].getString
          val data =
            result(1)
              .asInstanceOf[Array[Object]]
              .map(_.asInstanceOf[glide.api.models.GlideString])
          val pairs = data
            .grouped(2)
            .collect { case Array(member, score) =>
              (valueCodec.decode(member), score.getString.toDouble)
            }
            .toList
          ScanResult(nextCursor, pairs)
        }
    }

  // ==================== Scripting Commands ====================

  override def fcall(
      function: K,
      keys: List[K],
      args: List[K]
  ): F[ValkeyResponse[String]] =
    exec("FCALL") {
      baseClient
        .fcall(
          keyCodec.encode(function),
          keys.map(keyCodec.encode).toArray,
          args.map(keyCodec.encode).toArray
        )
        .futureLift
        .map(_.toString)
    }

  override def fcallReadOnly(
      function: K,
      keys: List[K],
      args: List[K]
  ): F[ValkeyResponse[String]] =
    exec("FCALL_RO") {
      baseClient
        .fcallReadOnly(
          keyCodec.encode(function),
          keys.map(keyCodec.encode).toArray,
          args.map(keyCodec.encode).toArray
        )
        .futureLift
        .map(_.toString)
    }

  override def scriptFlush: F[ValkeyResponse[Unit]] =
    exec("SCRIPT FLUSH") {
      baseClient.scriptFlush().futureLift.void
    }

  override def scriptFlush(
      mode: arguments.FlushMode
  ): F[ValkeyResponse[Unit]] =
    exec("SCRIPT FLUSH") {
      baseClient.scriptFlush(FlushMode.toGlide(mode)).futureLift.void
    }

  override def scriptKill: F[ValkeyResponse[Unit]] =
    exec("SCRIPT KILL") {
      baseClient.scriptKill().futureLift.void
    }

  override def scriptExists(
      sha1s: String*
  ): F[ValkeyResponse[List[Boolean]]] =
    exec("SCRIPT EXISTS") {
      baseClient
        .scriptExists(
          sha1s.map(glide.api.models.GlideString.of).toArray
        )
        .futureLift
        .map(_.toList.map(_.booleanValue()))
    }

  // ==================== Server Management Commands ====================

  private def extractClusterInfo(
      cv: glide.api.models.ClusterValue[String]
  ): String =
    if (cv.hasSingleData) cv.getSingleValue
    else cv.getMultiValue.asScala.values.mkString("\n")

  override def info: F[ValkeyResponse[String]] =
    serverCmd("INFO")(
      _.info().futureLift,
      _.info().futureLift.map(extractClusterInfo)
    )

  override def info(sections: Set[InfoSection]): F[ValkeyResponse[String]] = {
    val sectionsArray = InfoSection.toGlideArray(sections)
    serverCmd("INFO with sections")(
      _.info(sectionsArray).futureLift,
      _.info(sectionsArray).futureLift.map(extractClusterInfo)
    )
  }

  override def configRewrite: F[ValkeyResponse[Unit]] =
    serverCmd("CONFIG REWRITE")(
      _.configRewrite().futureLift.void,
      _.configRewrite().futureLift.void
    )

  override def configResetStat: F[ValkeyResponse[Unit]] =
    serverCmd("CONFIG RESETSTAT")(
      _.configResetStat().futureLift.void,
      _.configResetStat().futureLift.void
    )

  override def configGet(
      parameters: Set[String]
  ): F[ValkeyResponse[Map[String, String]]] = {
    val paramsArray = parameters.toArray
    serverCmd("CONFIG GET")(
      _.configGet(paramsArray).futureLift.map(_.asScala.toMap),
      _.configGet(paramsArray).futureLift.map(_.asScala.toMap)
    )
  }

  override def configSet(
      parameters: Map[String, String]
  ): F[ValkeyResponse[Unit]] = {
    val javaMap = parameters.asJava
    serverCmd("CONFIG SET")(
      _.configSet(javaMap).futureLift.void,
      _.configSet(javaMap).futureLift.void
    )
  }

  private def parseTime(array: Array[String]): ServerTime = {
    val seconds = array(0).toLong
    val microseconds = array(1).toLong
    ServerTime(seconds, microseconds)
  }

  override def time: F[ValkeyResponse[ServerTime]] =
    serverCmd("TIME")(
      _.time().futureLift.map(parseTime),
      _.time().futureLift.map(parseTime)
    )

  override def lastSave: F[ValkeyResponse[Long]] =
    serverCmd("LASTSAVE")(
      _.lastsave().futureLift.map(_.longValue()),
      _.lastsave().futureLift.map(_.longValue())
    )

  override def flushAll: F[ValkeyResponse[Unit]] =
    serverCmd("FLUSHALL")(
      _.flushall().futureLift.void,
      _.flushall().futureLift.void
    )

  override def flushAll(mode: FlushMode): F[ValkeyResponse[Unit]] = {
    val glideMode = FlushMode.toGlide(mode)
    serverCmd(s"FLUSHALL with mode $mode")(
      _.flushall(glideMode).futureLift.void,
      _.flushall(glideMode).futureLift.void
    )
  }

  override def flushDB: F[ValkeyResponse[Unit]] =
    serverCmd("FLUSHDB")(
      _.flushdb().futureLift.void,
      _.flushdb().futureLift.void
    )

  override def flushDB(mode: FlushMode): F[ValkeyResponse[Unit]] = {
    val glideMode = FlushMode.toGlide(mode)
    serverCmd(s"FLUSHDB with mode $mode")(
      _.flushdb(glideMode).futureLift.void,
      _.flushdb(glideMode).futureLift.void
    )
  }

  override def lolwut: F[ValkeyResponse[String]] =
    serverCmd("LOLWUT")(
      _.lolwut().futureLift,
      _.lolwut().futureLift
    )

  override def lolwut(version: Int): F[ValkeyResponse[String]] =
    serverCmd(s"LOLWUT version $version")(
      _.lolwut(version).futureLift,
      _.lolwut(version).futureLift
    )

  override def lolwut(
      version: Int,
      parameters: List[Int]
  ): F[ValkeyResponse[String]] = {
    val paramsArray = parameters.toArray
    serverCmd(s"LOLWUT version $version")(
      _.lolwut(version, paramsArray).futureLift,
      _.lolwut(version, paramsArray).futureLift
    )
  }

  override def dbSize: F[ValkeyResponse[Long]] =
    serverCmd("DBSIZE")(
      _.dbsize().futureLift.map(_.longValue()),
      _.dbsize().futureLift.map(_.longValue())
    )

  // ==================== Connection Commands ====================

  override def ping: F[ValkeyResponse[String]] =
    serverCmd("PING")(
      _.ping().futureLift,
      _.ping().futureLift
    )

  override def ping(message: V): F[ValkeyResponse[V]] =
    serverCmd("PING")(
      _.ping(valueCodec.encode(message)).futureLift.map(valueCodec.decode),
      _.ping(valueCodec.encode(message)).futureLift.map(valueCodec.decode)
    )

  override def echo(message: V): F[ValkeyResponse[V]] =
    serverCmd("ECHO")(
      _.echo(valueCodec.encode(message)).futureLift.map(valueCodec.decode),
      _.echo(valueCodec.encode(message)).futureLift.map(valueCodec.decode)
    )

  override def clientId: F[ValkeyResponse[Long]] =
    serverCmd("CLIENT ID")(
      _.clientId().futureLift.map(_.longValue()),
      _.clientId().futureLift.map(_.longValue())
    )

  override def clientGetName: F[ValkeyResponse[Option[String]]] =
    serverCmd("CLIENT GETNAME")(
      _.clientGetName().futureLift.map(Option(_)),
      _.clientGetName().futureLift.map(Option(_))
    )

  override def select(index: Long): F[ValkeyResponse[Unit]] =
    serverCmd("SELECT")(
      _.select(index).futureLift.void,
      _.select(index).futureLift.void
    )

  // ==================== HyperLogLog Commands ====================

  override def pfadd(key: K, elements: V*): F[ValkeyResponse[Boolean]] =
    exec(s"PFADD $key") {
      val keyGS = keyCodec.encode(key)
      val elemsArray = elements.map(valueCodec.encode).toArray
      baseClient.pfadd(keyGS, elemsArray).futureLift.map(_.booleanValue())
    }

  override def pfcount(keys: K*): F[ValkeyResponse[Long]] =
    exec("PFCOUNT") {
      val keysArray = keys.map(keyCodec.encode).toArray
      baseClient.pfcount(keysArray).futureLift.map(_.longValue())
    }

  override def pfmerge(destkey: K, sourcekeys: K*): F[ValkeyResponse[Unit]] =
    exec(s"PFMERGE $destkey") {
      val destGS = keyCodec.encode(destkey)
      val sourcesArray = sourcekeys.map(keyCodec.encode).toArray
      baseClient.pfmerge(destGS, sourcesArray).futureLift.void
    }

  // ==================== Geo Commands ====================

  override def geoAdd(
      key: K,
      members: Map[V, GeoPosition]
  ): F[ValkeyResponse[Long]] =
    exec(s"GEOADD $key") {
      val keyGS = keyCodec.encode(key)
      val javaMap =
        new java.util.HashMap[
          glide.api.models.GlideString,
          glide.api.models.commands.geospatial.GeospatialData
        ]()
      members.foreach { case (member, pos) =>
        javaMap.put(valueCodec.encode(member), pos.toGlide)
      }
      baseClient.geoadd(keyGS, javaMap).futureLift.map(_.longValue())
    }

  override def geoAdd(
      key: K,
      members: Map[V, GeoPosition],
      options: GeoAddOptions
  ): F[ValkeyResponse[Long]] =
    exec(s"GEOADD $key") {
      val keyGS = keyCodec.encode(key)
      val javaMap =
        new java.util.HashMap[
          glide.api.models.GlideString,
          glide.api.models.commands.geospatial.GeospatialData
        ]()
      members.foreach { case (member, pos) =>
        javaMap.put(valueCodec.encode(member), pos.toGlide)
      }
      baseClient
        .geoadd(keyGS, javaMap, options.toGlide)
        .futureLift
        .map(_.longValue())
    }

  override def geoDist(
      key: K,
      member1: V,
      member2: V
  ): F[ValkeyResponse[Option[Double]]] =
    exec(s"GEODIST $key") {
      val keyGS = keyCodec.encode(key)
      baseClient
        .geodist(keyGS, valueCodec.encode(member1), valueCodec.encode(member2))
        .futureLift
        .map(d => Option(d).map(_.doubleValue()))
    }

  override def geoDist(
      key: K,
      member1: V,
      member2: V,
      unit: GeoUnit
  ): F[ValkeyResponse[Option[Double]]] =
    exec(s"GEODIST $key") {
      val keyGS = keyCodec.encode(key)
      baseClient
        .geodist(
          keyGS,
          valueCodec.encode(member1),
          valueCodec.encode(member2),
          unit.toGlide
        )
        .futureLift
        .map(d => Option(d).map(_.doubleValue()))
    }

  override def geoHash(
      key: K,
      members: V*
  ): F[ValkeyResponse[List[Option[String]]]] =
    exec(s"GEOHASH $key") {
      val keyGS = keyCodec.encode(key)
      val membersArray = members.map(valueCodec.encode).toArray
      baseClient
        .geohash(keyGS, membersArray)
        .futureLift
        .map { arr =>
          arr.toList.map(gs => Option(gs).map(_.toString))
        }
    }

  override def geoPos(
      key: K,
      members: V*
  ): F[ValkeyResponse[List[Option[GeoPosition]]]] =
    exec(s"GEOPOS $key") {
      val keyGS = keyCodec.encode(key)
      val membersArray = members.map(valueCodec.encode).toArray
      baseClient
        .geopos(keyGS, membersArray)
        .futureLift
        .map { arr =>
          arr.toList.map { coords =>
            if (coords == null || coords.length < 2 || coords(0) == null)
              None
            else
              Some(
                GeoPosition(coords(0).doubleValue(), coords(1).doubleValue())
              )
          }
        }
    }

  override def geoSearch(
      key: K,
      from: GeoSearchFrom[K],
      by: GeoSearchBy
  ): F[ValkeyResponse[List[V]]] =
    exec(s"GEOSEARCH $key") {
      val keyGS = keyCodec.encode(key)
      baseClient
        .geosearch(keyGS, from.toGlide(keyCodec.encode), by.toGlide)
        .futureLift
        .map { arr =>
          arr.toList.map(gs => valueCodec.decode(gs))
        }
    }

  override def geoSearch(
      key: K,
      from: GeoSearchFrom[K],
      by: GeoSearchBy,
      resultOptions: GeoSearchResultOptions
  ): F[ValkeyResponse[List[V]]] =
    exec(s"GEOSEARCH $key") {
      val keyGS = keyCodec.encode(key)
      baseClient
        .geosearch(
          keyGS,
          from.toGlide(keyCodec.encode),
          by.toGlide,
          resultOptions.toGlide
        )
        .futureLift
        .map { arr =>
          arr.toList.map(gs => valueCodec.decode(gs))
        }
    }

  override def geoSearchStore(
      destination: K,
      source: K,
      from: GeoSearchFrom[K],
      by: GeoSearchBy
  ): F[ValkeyResponse[Long]] =
    exec(s"GEOSEARCHSTORE $destination $source") {
      baseClient
        .geosearchstore(
          keyCodec.encode(destination),
          keyCodec.encode(source),
          from.toGlide(keyCodec.encode),
          by.toGlide
        )
        .futureLift
        .map(_.longValue())
    }

  override def geoSearchStore(
      destination: K,
      source: K,
      from: GeoSearchFrom[K],
      by: GeoSearchBy,
      resultOptions: GeoSearchResultOptions
  ): F[ValkeyResponse[Long]] =
    exec(s"GEOSEARCHSTORE $destination $source") {
      baseClient
        .geosearchstore(
          keyCodec.encode(destination),
          keyCodec.encode(source),
          from.toGlide(keyCodec.encode),
          by.toGlide,
          resultOptions.toGlide
        )
        .futureLift
        .map(_.longValue())
    }

  // ==================== Bitmap Commands ====================

  override def setbit(
      key: K,
      offset: Long,
      value: Long
  ): F[ValkeyResponse[Long]] =
    exec(s"SETBIT $key") {
      baseClient
        .setbit(keyCodec.encode(key), offset, value)
        .futureLift
        .map(_.longValue())
    }

  override def getbit(key: K, offset: Long): F[ValkeyResponse[Long]] =
    exec(s"GETBIT $key") {
      baseClient
        .getbit(keyCodec.encode(key), offset)
        .futureLift
        .map(_.longValue())
    }

  override def bitcount(key: K): F[ValkeyResponse[Long]] =
    exec(s"BITCOUNT $key") {
      baseClient.bitcount(keyCodec.encode(key)).futureLift.map(_.longValue())
    }

  override def bitcount(
      key: K,
      start: Long,
      end: Long
  ): F[ValkeyResponse[Long]] =
    exec(s"BITCOUNT $key") {
      baseClient
        .bitcount(keyCodec.encode(key), start, end)
        .futureLift
        .map(_.longValue())
    }

  override def bitcount(
      key: K,
      start: Long,
      end: Long,
      indexType: BitmapIndexType
  ): F[ValkeyResponse[Long]] =
    exec(s"BITCOUNT $key") {
      baseClient
        .bitcount(keyCodec.encode(key), start, end, indexType.toGlide)
        .futureLift
        .map(_.longValue())
    }

  override def bitpos(key: K, bit: Long): F[ValkeyResponse[Long]] =
    exec(s"BITPOS $key") {
      baseClient
        .bitpos(keyCodec.encode(key), bit)
        .futureLift
        .map(_.longValue())
    }

  override def bitpos(
      key: K,
      bit: Long,
      start: Long
  ): F[ValkeyResponse[Long]] =
    exec(s"BITPOS $key") {
      baseClient
        .bitpos(keyCodec.encode(key), bit, start)
        .futureLift
        .map(_.longValue())
    }

  override def bitpos(
      key: K,
      bit: Long,
      start: Long,
      end: Long
  ): F[ValkeyResponse[Long]] =
    exec(s"BITPOS $key") {
      baseClient
        .bitpos(keyCodec.encode(key), bit, start, end)
        .futureLift
        .map(_.longValue())
    }

  override def bitpos(
      key: K,
      bit: Long,
      start: Long,
      end: Long,
      indexType: BitmapIndexType
  ): F[ValkeyResponse[Long]] =
    exec(s"BITPOS $key") {
      baseClient
        .bitpos(keyCodec.encode(key), bit, start, end, indexType.toGlide)
        .futureLift
        .map(_.longValue())
    }

  override def bitop(
      operation: BitwiseOperation,
      destkey: K,
      keys: K*
  ): F[ValkeyResponse[Long]] =
    exec(s"BITOP $operation") {
      baseClient
        .bitop(
          operation.toGlide,
          keyCodec.encode(destkey),
          keys.map(keyCodec.encode).toArray
        )
        .futureLift
        .map(_.longValue())
    }

  // ==================== PubSub Commands ====================

  override def publish(channel: K, message: V): F[ValkeyResponse[Unit]] =
    exec("PUBLISH") {
      baseClient
        .publish(keyCodec.encode(channel), valueCodec.encode(message))
        .futureLift
        .void
    }

  override def pubsubChannels: F[ValkeyResponse[List[K]]] =
    exec("PUBSUB CHANNELS") {
      baseClient
        .pubsubChannelsBinary()
        .futureLift
        .map(_.toList.map(keyCodec.decode))
    }

  override def pubsubChannels(pattern: K): F[ValkeyResponse[List[K]]] =
    exec("PUBSUB CHANNELS") {
      baseClient
        .pubsubChannels(keyCodec.encode(pattern))
        .futureLift
        .map(_.toList.map(keyCodec.decode))
    }

  override def pubsubNumPat: F[ValkeyResponse[Long]] =
    exec("PUBSUB NUMPAT") {
      baseClient
        .pubsubNumPat()
        .futureLift
        .map(_.longValue())
    }

  override def pubsubNumSub(channels: K*): F[ValkeyResponse[Map[K, Long]]] = {
    if (channels.isEmpty) Async[F].pure(ValkeyResponse.ok(Map.empty[K, Long]))
    else
      exec("PUBSUB NUMSUB") {
        baseClient
          .pubsubNumSub(channels.map(keyCodec.encode).toArray)
          .futureLift
          .map(
            _.asScala
              .map { case (k, v) =>
                (keyCodec.decode(k), v.longValue())
              }
              .toMap
          )
      }
  }

  // ==================== Stream Commands ====================

  private def parseStreamEntries(
      result: java.util.Map[
        glide.api.models.GlideString,
        Array[Array[glide.api.models.GlideString]]
      ]
  ): Map[String, List[(K, V)]] =
    if (result == null) Map.empty
    else
      result.asScala.map { case (id, fieldValues) =>
        val pairs = fieldValues.toList.map { pair =>
          (keyCodec.decode(pair(0)), valueCodec.decode(pair(1)))
        }
        (id.getString, pairs)
      }.toMap

  override def xadd(
      key: K,
      fieldValues: Map[K, V]
  ): F[ValkeyResponse[String]] =
    exec("XADD") {
      val glideMap = new java.util.LinkedHashMap[
        glide.api.models.GlideString,
        glide.api.models.GlideString
      ]()
      fieldValues.foreach { case (k, v) =>
        glideMap.put(keyCodec.encode(k), valueCodec.encode(v))
      }
      baseClient
        .xadd(keyCodec.encode(key), glideMap)
        .futureLift
        .map(_.getString)
    }

  override def xlen(key: K): F[ValkeyResponse[Long]] =
    exec("XLEN") {
      baseClient
        .xlen(keyCodec.encode(key))
        .futureLift
        .map(_.longValue())
    }

  override def xdel(key: K, ids: String*): F[ValkeyResponse[Long]] =
    exec("XDEL") {
      baseClient
        .xdel(
          keyCodec.encode(key),
          ids.map(glide.api.models.GlideString.of).toArray
        )
        .futureLift
        .map(_.longValue())
    }

  override def xtrim(
      key: K,
      strategy: StreamTrimStrategy
  ): F[ValkeyResponse[Long]] =
    exec("XTRIM") {
      baseClient
        .xtrim(keyCodec.encode(key), strategy.toGlide)
        .futureLift
        .map(_.longValue())
    }

  override def xrange(
      key: K,
      start: StreamRangeBound,
      end: StreamRangeBound
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]] =
    exec("XRANGE") {
      baseClient
        .xrange(keyCodec.encode(key), start.toGlide, end.toGlide)
        .futureLift
        .map(parseStreamEntries)
    }

  override def xrange(
      key: K,
      start: StreamRangeBound,
      end: StreamRangeBound,
      count: Long
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]] =
    exec("XRANGE") {
      baseClient
        .xrange(keyCodec.encode(key), start.toGlide, end.toGlide, count)
        .futureLift
        .map(parseStreamEntries)
    }

  override def xrevrange(
      key: K,
      end: StreamRangeBound,
      start: StreamRangeBound
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]] =
    exec("XREVRANGE") {
      baseClient
        .xrevrange(keyCodec.encode(key), end.toGlide, start.toGlide)
        .futureLift
        .map(parseStreamEntries)
    }

  override def xrevrange(
      key: K,
      end: StreamRangeBound,
      start: StreamRangeBound,
      count: Long
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]] =
    exec("XREVRANGE") {
      baseClient
        .xrevrange(
          keyCodec.encode(key),
          end.toGlide,
          start.toGlide,
          count
        )
        .futureLift
        .map(parseStreamEntries)
    }

  override def xgroupCreate(
      key: K,
      group: K,
      id: String
  ): F[ValkeyResponse[Unit]] =
    exec("XGROUP CREATE") {
      baseClient
        .xgroupCreate(
          keyCodec.encode(key),
          keyCodec.encode(group),
          glide.api.models.GlideString.of(id)
        )
        .futureLift
        .void
    }

  override def xgroupCreate(
      key: K,
      group: K,
      id: String,
      mkStream: Boolean
  ): F[ValkeyResponse[Unit]] =
    if (!mkStream) xgroupCreate(key, group, id)
    else
      exec("XGROUP CREATE") {
        val opts = glide.api.models.commands.stream.StreamGroupOptions
          .builder()
          .makeStream()
          .build()
        baseClient
          .xgroupCreate(
            keyCodec.encode(key),
            keyCodec.encode(group),
            glide.api.models.GlideString.of(id),
            opts
          )
          .futureLift
          .void
      }

  override def xgroupDestroy(key: K, group: K): F[ValkeyResponse[Boolean]] =
    exec("XGROUP DESTROY") {
      baseClient
        .xgroupDestroy(keyCodec.encode(key), keyCodec.encode(group))
        .futureLift
        .map(_.booleanValue())
    }

  override def xgroupCreateConsumer(
      key: K,
      group: K,
      consumer: K
  ): F[ValkeyResponse[Boolean]] =
    exec("XGROUP CREATECONSUMER") {
      baseClient
        .xgroupCreateConsumer(
          keyCodec.encode(key),
          keyCodec.encode(group),
          keyCodec.encode(consumer)
        )
        .futureLift
        .map(_.booleanValue())
    }

  override def xgroupDelConsumer(
      key: K,
      group: K,
      consumer: K
  ): F[ValkeyResponse[Long]] =
    exec("XGROUP DELCONSUMER") {
      baseClient
        .xgroupDelConsumer(
          keyCodec.encode(key),
          keyCodec.encode(group),
          keyCodec.encode(consumer)
        )
        .futureLift
        .map(_.longValue())
    }

  override def xgroupSetId(
      key: K,
      group: K,
      id: String
  ): F[ValkeyResponse[Unit]] =
    exec("XGROUP SETID") {
      baseClient
        .xgroupSetId(
          keyCodec.encode(key),
          keyCodec.encode(group),
          glide.api.models.GlideString.of(id)
        )
        .futureLift
        .void
    }

  override def xack(
      key: K,
      group: K,
      ids: String*
  ): F[ValkeyResponse[Long]] =
    exec("XACK") {
      baseClient
        .xack(
          keyCodec.encode(key),
          keyCodec.encode(group),
          ids.map(glide.api.models.GlideString.of).toArray
        )
        .futureLift
        .map(_.longValue())
    }

  private type StreamReadResult = Map[K, Map[String, List[(K, V)]]]

  private def parseXreadResult(
      result: java.util.Map[
        glide.api.models.GlideString,
        java.util.Map[
          glide.api.models.GlideString,
          Array[Array[glide.api.models.GlideString]]
        ]
      ]
  ): Option[StreamReadResult] =
    if (result == null) None
    else
      Some(
        result.asScala.map { case (streamKey, entries) =>
          val parsed = parseStreamEntries(entries)
          (keyCodec.decode(streamKey), parsed)
        }.toMap
      )

  override def xread(
      keysAndIds: Map[K, String]
  ): F[ValkeyResponse[Option[StreamReadResult]]] =
    exec("XREAD") {
      val glideMap =
        new java.util.LinkedHashMap[
          glide.api.models.GlideString,
          glide.api.models.GlideString
        ]()
      keysAndIds.foreach { case (k, id) =>
        glideMap.put(keyCodec.encode(k), glide.api.models.GlideString.of(id))
      }
      baseClient
        .xreadBinary(glideMap)
        .futureLift
        .map(parseXreadResult)
    }

  override def xread(
      keysAndIds: Map[K, String],
      count: Long,
      block: Long
  ): F[ValkeyResponse[Option[StreamReadResult]]] =
    exec("XREAD") {
      val glideMap =
        new java.util.LinkedHashMap[
          glide.api.models.GlideString,
          glide.api.models.GlideString
        ]()
      keysAndIds.foreach { case (k, id) =>
        glideMap.put(keyCodec.encode(k), glide.api.models.GlideString.of(id))
      }
      val opts = glide.api.models.commands.stream.StreamReadOptions
        .builder()
        .count(count)
        .block(block)
        .build()
      baseClient
        .xreadBinary(glideMap, opts)
        .futureLift
        .map(parseXreadResult)
    }

  override def xreadgroup(
      group: K,
      consumer: K,
      keysAndIds: Map[K, String]
  ): F[ValkeyResponse[Option[StreamReadResult]]] =
    exec("XREADGROUP") {
      val glideMap =
        new java.util.LinkedHashMap[
          glide.api.models.GlideString,
          glide.api.models.GlideString
        ]()
      keysAndIds.foreach { case (k, id) =>
        glideMap.put(keyCodec.encode(k), glide.api.models.GlideString.of(id))
      }
      baseClient
        .xreadgroup(glideMap, keyCodec.encode(group), keyCodec.encode(consumer))
        .futureLift
        .map(parseXreadResult)
    }

  override def xreadgroup(
      group: K,
      consumer: K,
      keysAndIds: Map[K, String],
      count: Long,
      block: Long
  ): F[ValkeyResponse[Option[StreamReadResult]]] =
    xreadgroup(group, consumer, keysAndIds, count, block, noAck = false)

  override def xreadgroup(
      group: K,
      consumer: K,
      keysAndIds: Map[K, String],
      count: Long,
      block: Long,
      noAck: Boolean
  ): F[ValkeyResponse[Option[StreamReadResult]]] =
    exec("XREADGROUP") {
      val glideMap =
        new java.util.LinkedHashMap[
          glide.api.models.GlideString,
          glide.api.models.GlideString
        ]()
      keysAndIds.foreach { case (k, id) =>
        glideMap.put(keyCodec.encode(k), glide.api.models.GlideString.of(id))
      }
      val opts = {
        val b = glide.api.models.commands.stream.StreamReadGroupOptions
          .builder()
          .count(count)
          .block(block)
        (if (noAck)
           b.asInstanceOf[
             glide.api.models.commands.stream.StreamReadGroupOptions.StreamReadGroupOptionsBuilder[
               glide.api.models.commands.stream.StreamReadGroupOptions,
               ?
             ]
           ].noack()
         else b).build()
      }.asInstanceOf[glide.api.models.commands.stream.StreamReadGroupOptions]
      baseClient
        .xreadgroup(
          glideMap,
          keyCodec.encode(group),
          keyCodec.encode(consumer),
          opts
        )
        .futureLift
        .map(parseXreadResult)
    }

  override def xclaim(
      key: K,
      group: K,
      consumer: K,
      minIdleTimeMillis: Long,
      ids: String*
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]] =
    exec("XCLAIM") {
      baseClient
        .xclaim(
          keyCodec.encode(key),
          keyCodec.encode(group),
          keyCodec.encode(consumer),
          minIdleTimeMillis,
          ids.map(glide.api.models.GlideString.of).toArray
        )
        .futureLift
        .map(parseStreamEntries)
    }

  override def xpendingSummary(
      key: K,
      group: K
  ): F[ValkeyResponse[PendingSummary[K]]] =
    exec("XPENDING") {
      baseClient
        .xpending(keyCodec.encode(key), keyCodec.encode(group))
        .futureLift
        .map { result =>
          val count = result(0).asInstanceOf[java.lang.Long].longValue()
          val smallest =
            Option(result(1)).map(
              _.asInstanceOf[glide.api.models.GlideString].getString
            )
          val greatest =
            Option(result(2)).map(
              _.asInstanceOf[glide.api.models.GlideString].getString
            )
          val consumers =
            if (result(3) == null) List.empty[PendingSummary.ConsumerPending[K]]
            else
              result(3)
                .asInstanceOf[Array[Object]]
                .toList
                .map { entry =>
                  val arr = entry.asInstanceOf[Array[Object]]
                  val name = keyCodec.decode(
                    arr(0).asInstanceOf[glide.api.models.GlideString]
                  )
                  val pending =
                    arr(1)
                      .asInstanceOf[glide.api.models.GlideString]
                      .getString
                      .toLong
                  PendingSummary.ConsumerPending(name, pending)
                }
          PendingSummary(count, smallest, greatest, consumers)
        }
    }

  override def xpendingRange(
      key: K,
      group: K,
      start: StreamRangeBound,
      end: StreamRangeBound,
      count: Long
  ): F[ValkeyResponse[List[PendingEntry[K]]]] =
    exec("XPENDING RANGE") {
      baseClient
        .xpending(
          keyCodec.encode(key),
          keyCodec.encode(group),
          start.toGlide,
          end.toGlide,
          count
        )
        .futureLift
        .map { result =>
          if (result == null) List.empty
          else
            result.toList.map { entry =>
              PendingEntry(
                messageId =
                  entry(0).asInstanceOf[glide.api.models.GlideString].getString,
                consumer = keyCodec.decode(
                  entry(1).asInstanceOf[glide.api.models.GlideString]
                ),
                idleTimeMillis =
                  entry(2).asInstanceOf[java.lang.Long].longValue(),
                deliveryCount =
                  entry(3).asInstanceOf[java.lang.Long].longValue()
              )
            }
        }
    }

  private def parseXautoclaimResult(
      result: Array[Object]
  ): AutoClaimResult[K, V] = {
    val nextCursor =
      result(0).asInstanceOf[glide.api.models.GlideString].getString
    val rawMap = result(1)
      .asInstanceOf[java.util.Map[glide.api.models.GlideString, Array[Object]]]
    val entries =
      if (rawMap == null) Map.empty[String, List[(K, V)]]
      else
        rawMap.asScala.map { case (id, fieldValues) =>
          val pairs = fieldValues.toList
            .map(_.asInstanceOf[Array[Object]])
            .map { pair =>
              (
                keyCodec.decode(
                  pair(0).asInstanceOf[glide.api.models.GlideString]
                ),
                valueCodec.decode(
                  pair(1).asInstanceOf[glide.api.models.GlideString]
                )
              )
            }
          (id.getString, pairs)
        }.toMap
    val deleted =
      if (result.length > 2 && result(2) != null)
        result(2)
          .asInstanceOf[Array[Object]]
          .toList
          .map(_.asInstanceOf[glide.api.models.GlideString].getString)
      else List.empty[String]
    AutoClaimResult(nextCursor, entries, deleted)
  }

  override def xautoclaim(
      key: K,
      group: K,
      consumer: K,
      minIdleTimeMillis: Long,
      start: String
  ): F[ValkeyResponse[AutoClaimResult[K, V]]] =
    exec("XAUTOCLAIM") {
      baseClient
        .xautoclaim(
          keyCodec.encode(key),
          keyCodec.encode(group),
          keyCodec.encode(consumer),
          minIdleTimeMillis,
          glide.api.models.GlideString.of(start)
        )
        .futureLift
        .map(parseXautoclaimResult)
    }

  override def xautoclaim(
      key: K,
      group: K,
      consumer: K,
      minIdleTimeMillis: Long,
      start: String,
      count: Long
  ): F[ValkeyResponse[AutoClaimResult[K, V]]] =
    exec("XAUTOCLAIM") {
      baseClient
        .xautoclaim(
          keyCodec.encode(key),
          keyCodec.encode(group),
          keyCodec.encode(consumer),
          minIdleTimeMillis,
          glide.api.models.GlideString.of(start),
          count
        )
        .futureLift
        .map(parseXautoclaimResult)
    }

  override def xautoclaimJustId(
      key: K,
      group: K,
      consumer: K,
      minIdleTimeMillis: Long,
      start: String
  ): F[ValkeyResponse[AutoClaimIdResult]] =
    exec("XAUTOCLAIM JUSTID") {
      baseClient
        .xautoclaimJustId(
          keyCodec.encode(key),
          keyCodec.encode(group),
          keyCodec.encode(consumer),
          minIdleTimeMillis,
          glide.api.models.GlideString.of(start)
        )
        .futureLift
        .map { result =>
          val nextCursor =
            result(0).asInstanceOf[glide.api.models.GlideString].getString
          val claimedIds =
            result(1)
              .asInstanceOf[Array[Object]]
              .toList
              .map(_.asInstanceOf[glide.api.models.GlideString].getString)
          val deleted =
            if (result.length > 2 && result(2) != null)
              result(2)
                .asInstanceOf[Array[Object]]
                .toList
                .map(_.asInstanceOf[glide.api.models.GlideString].getString)
            else List.empty[String]
          AutoClaimIdResult(nextCursor, claimedIds, deleted)
        }
    }
}

/** Standalone client commands implementation */
private[valkey4cats] class ValkeyStandalone[F[_]: MkValkey: Async, K, V](
    client: ValkeyClient,
    keyCodec: Codec[K],
    valueCodec: Codec[V],
    tx: TxRunner[F]
) extends BaseValkey[F, K, V](
      ValkeyConnection.Standalone(client),
      keyCodec,
      valueCodec,
      tx
    )

/** Cluster client commands implementation */
private[valkey4cats] class ValkeyCluster[F[_]: MkValkey: Async, K, V](
    client: ValkeyClusterClient,
    keyCodec: Codec[K],
    valueCodec: Codec[V],
    tx: TxRunner[F]
) extends BaseValkey[F, K, V](
      ValkeyConnection.Clustered(client),
      keyCodec,
      valueCodec,
      tx
    )
