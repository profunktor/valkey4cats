package dev.profunktor.valkey4cats.ffi

import dev.profunktor.valkey4cats.arguments.*
import java.nio.charset.StandardCharsets

private[valkey4cats] object ArgEncoder:

  // Cached constant byte arrays — avoid re-allocation per call
  val NX: Array[Byte] = "NX".getBytes(StandardCharsets.UTF_8)
  val XX: Array[Byte] = "XX".getBytes(StandardCharsets.UTF_8)
  val GT: Array[Byte] = "GT".getBytes(StandardCharsets.UTF_8)
  val LT: Array[Byte] = "LT".getBytes(StandardCharsets.UTF_8)
  val LEFT: Array[Byte] = "LEFT".getBytes(StandardCharsets.UTF_8)
  val RIGHT: Array[Byte] = "RIGHT".getBytes(StandardCharsets.UTF_8)
  val WITHSCORES: Array[Byte] = "WITHSCORES".getBytes(StandardCharsets.UTF_8)
  val WITHSCORE: Array[Byte] = "WITHSCORE".getBytes(StandardCharsets.UTF_8)
  val WITHVALUES: Array[Byte] = "WITHVALUES".getBytes(StandardCharsets.UTF_8)
  val COUNT: Array[Byte] = "COUNT".getBytes(StandardCharsets.UTF_8)
  val FIELDS: Array[Byte] = "FIELDS".getBytes(StandardCharsets.UTF_8)
  val MATCH: Array[Byte] = "MATCH".getBytes(StandardCharsets.UTF_8)
  val EX: Array[Byte] = "EX".getBytes(StandardCharsets.UTF_8)
  val PX: Array[Byte] = "PX".getBytes(StandardCharsets.UTF_8)
  val EXAT: Array[Byte] = "EXAT".getBytes(StandardCharsets.UTF_8)
  val PXAT: Array[Byte] = "PXAT".getBytes(StandardCharsets.UTF_8)
  val PERSIST: Array[Byte] = "PERSIST".getBytes(StandardCharsets.UTF_8)
  val KEEPTTL: Array[Byte] = "KEEPTTL".getBytes(StandardCharsets.UTF_8)
  val GET: Array[Byte] = "GET".getBytes(StandardCharsets.UTF_8)
  val STORE: Array[Byte] = "STORE".getBytes(StandardCharsets.UTF_8)
  val LEN: Array[Byte] = "LEN".getBytes(StandardCharsets.UTF_8)
  val BEFORE: Array[Byte] = "BEFORE".getBytes(StandardCharsets.UTF_8)
  val AFTER: Array[Byte] = "AFTER".getBytes(StandardCharsets.UTF_8)
  val IFEQ: Array[Byte] = "IFEQ".getBytes(StandardCharsets.UTF_8)
  val MIN: Array[Byte] = "MIN".getBytes(StandardCharsets.UTF_8)
  val MAX: Array[Byte] = "MAX".getBytes(StandardCharsets.UTF_8)
  val LIMIT: Array[Byte] = "LIMIT".getBytes(StandardCharsets.UTF_8)
  val AGGREGATE: Array[Byte] = "AGGREGATE".getBytes(StandardCharsets.UTF_8)
  val SUM: Array[Byte] = "SUM".getBytes(StandardCharsets.UTF_8)
  val ASC: Array[Byte] = "ASC".getBytes(StandardCharsets.UTF_8)
  val DESC: Array[Byte] = "DESC".getBytes(StandardCharsets.UTF_8)
  val ANY: Array[Byte] = "ANY".getBytes(StandardCharsets.UTF_8)
  val BYRADIUS: Array[Byte] = "BYRADIUS".getBytes(StandardCharsets.UTF_8)
  val BYBOX: Array[Byte] = "BYBOX".getBytes(StandardCharsets.UTF_8)
  val FROMLONLAT: Array[Byte] = "FROMLONLAT".getBytes(StandardCharsets.UTF_8)
  val FROMMEMBER: Array[Byte] = "FROMMEMBER".getBytes(StandardCharsets.UTF_8)
  val BYTE: Array[Byte] = "BYTE".getBytes(StandardCharsets.UTF_8)
  val BIT: Array[Byte] = "BIT".getBytes(StandardCharsets.UTF_8)
  val AND: Array[Byte] = "AND".getBytes(StandardCharsets.UTF_8)
  val OR: Array[Byte] = "OR".getBytes(StandardCharsets.UTF_8)
  val XOR: Array[Byte] = "XOR".getBytes(StandardCharsets.UTF_8)
  val NOT: Array[Byte] = "NOT".getBytes(StandardCharsets.UTF_8)
  val SYNC: Array[Byte] = "SYNC".getBytes(StandardCharsets.UTF_8)
  val ASYNC: Array[Byte] = "ASYNC".getBytes(StandardCharsets.UTF_8)
  val STREAMS: Array[Byte] = "STREAMS".getBytes(StandardCharsets.UTF_8)
  val GROUP: Array[Byte] = "GROUP".getBytes(StandardCharsets.UTF_8)
  val NOACK: Array[Byte] = "NOACK".getBytes(StandardCharsets.UTF_8)
  val BLOCK: Array[Byte] = "BLOCK".getBytes(StandardCharsets.UTF_8)
  val MKSTREAM: Array[Byte] = "MKSTREAM".getBytes(StandardCharsets.UTF_8)
  val MAXLEN: Array[Byte] = "MAXLEN".getBytes(StandardCharsets.UTF_8)
  val MINID: Array[Byte] = "MINID".getBytes(StandardCharsets.UTF_8)
  val JUSTID: Array[Byte] = "JUSTID".getBytes(StandardCharsets.UTF_8)
  val REV: Array[Byte] = "REV".getBytes(StandardCharsets.UTF_8)
  val BYSCORE: Array[Byte] = "BYSCORE".getBytes(StandardCharsets.UTF_8)
  val BYLEX: Array[Byte] = "BYLEX".getBytes(StandardCharsets.UTF_8)
  val FXX: Array[Byte] = "FXX".getBytes(StandardCharsets.UTF_8)
  val FNX: Array[Byte] = "FNX".getBytes(StandardCharsets.UTF_8)
  val INCR: Array[Byte] = "INCR".getBytes(StandardCharsets.UTF_8)
  val CH: Array[Byte] = "CH".getBytes(StandardCharsets.UTF_8)
  val APPROX: Array[Byte] = "~".getBytes(StandardCharsets.UTF_8)
  val STAR: Array[Byte] = "*".getBytes(StandardCharsets.UTF_8)

  // Cached fixed strings used in boundary/unit encoding
  private val POS_INF: Array[Byte] = "+inf".getBytes(StandardCharsets.UTF_8)
  private val NEG_INF: Array[Byte] = "-inf".getBytes(StandardCharsets.UTF_8)
  private val PLUS: Array[Byte] = "+".getBytes(StandardCharsets.UTF_8)
  private val MINUS: Array[Byte] = "-".getBytes(StandardCharsets.UTF_8)
  private val GEO_M: Array[Byte] = "m".getBytes(StandardCharsets.UTF_8)
  private val GEO_KM: Array[Byte] = "km".getBytes(StandardCharsets.UTF_8)
  private val GEO_MI: Array[Byte] = "mi".getBytes(StandardCharsets.UTF_8)
  private val GEO_FT: Array[Byte] = "ft".getBytes(StandardCharsets.UTF_8)

  def longBytes(n: Long): Array[Byte] = n.toString.getBytes(StandardCharsets.UTF_8)
  def doubleBytes(d: Double): Array[Byte] = d.toString.getBytes(StandardCharsets.UTF_8)
  def intBytes(n: Int): Array[Byte] = n.toString.getBytes(StandardCharsets.UTF_8)
  def strBytes(s: String): Array[Byte] = s.getBytes(StandardCharsets.UTF_8)

  def expireCondition(c: ExpireCondition): Array[Byte] = c match
    case ExpireCondition.OnlyIfNoExpiry  => NX
    case ExpireCondition.OnlyIfHasExpiry => XX
    case ExpireCondition.OnlyIfGreater   => GT
    case ExpireCondition.OnlyIfLess      => LT

  def direction(d: ListDirection): Array[Byte] = d match
    case ListDirection.Left  => LEFT
    case ListDirection.Right => RIGHT

  def scoreBoundary(b: ScoreBoundary): Array[Byte] = b match
    case ScoreBoundary.PositiveInfinity    => POS_INF
    case ScoreBoundary.NegativeInfinity    => NEG_INF
    case ScoreBoundary.Score(score, true)  => doubleBytes(score)
    case ScoreBoundary.Score(score, false) => strBytes(s"($score")

  def lexBoundary(b: LexBoundary): Array[Byte] = b match
    case LexBoundary.PositiveInfinity  => PLUS
    case LexBoundary.NegativeInfinity  => MINUS
    case LexBoundary.Lex(value, true)  => strBytes(s"[$value")
    case LexBoundary.Lex(value, false) => strBytes(s"($value")

  def geoUnit(u: GeoUnit): Array[Byte] = u match
    case GeoUnit.Meters     => GEO_M
    case GeoUnit.Kilometers => GEO_KM
    case GeoUnit.Miles      => GEO_MI
    case GeoUnit.Feet       => GEO_FT

  def flushMode(mode: FlushMode): Array[Byte] = mode match
    case FlushMode.Sync  => SYNC
    case FlushMode.Async => ASYNC

  def scoreFilter(f: ScoreFilter): Array[Byte] = f match
    case ScoreFilter.Min => MIN
    case ScoreFilter.Max => MAX

  def streamBound(b: StreamRangeBound): Array[Byte] = b match
    case StreamRangeBound.Min             => MINUS
    case StreamRangeBound.Max             => PLUS
    case StreamRangeBound.Id(id)          => strBytes(id)
    case StreamRangeBound.ExclusiveId(id) => strBytes(s"($id")

  def geoSearchBy(by: GeoSearchBy): Array[Array[Byte]] = by match
    case GeoSearchBy.ByRadius(radius, unit) => Array(BYRADIUS, doubleBytes(radius), geoUnit(unit))
    case GeoSearchBy.ByBox(w, h, unit)      => Array(BYBOX, doubleBytes(w), doubleBytes(h), geoUnit(unit))

  def geoResultOptions(opts: GeoSearchResultOptions): Array[Array[Byte]] =
    val sortArgs: Array[Array[Byte]] = opts.sortOrder match
      case Some(SortOrder.Asc)  => Array(ASC)
      case Some(SortOrder.Desc) => Array(DESC)
      case None                 => Array.empty
    val countArgs: Array[Array[Byte]] = opts.count match
      case Some(c) if opts.any => Array(COUNT, longBytes(c), ANY)
      case Some(c)             => Array(COUNT, longBytes(c))
      case None                => Array.empty
    sortArgs ++ countArgs

  def aggregate(agg: AggregateOption): Array[Byte] = agg match
    case AggregateOption.Sum => SUM
    case AggregateOption.Min => MIN
    case AggregateOption.Max => MAX

  def bitwiseOp(op: BitwiseOperation): Array[Byte] = op match
    case BitwiseOperation.And => AND
    case BitwiseOperation.Or  => OR
    case BitwiseOperation.Xor => XOR
    case BitwiseOperation.Not => NOT

  def bitmapIndexType(t: BitmapIndexType): Array[Byte] = t match
    case BitmapIndexType.Byte => BYTE
    case BitmapIndexType.Bit  => BIT

  def getExExpiry(expiry: GetExExpiry): Array[Array[Byte]] = expiry match
    case GetExExpiry.Seconds(s)           => Array(EX, longBytes(s))
    case GetExExpiry.Milliseconds(ms)     => Array(PX, longBytes(ms))
    case GetExExpiry.UnixSeconds(ts)      => Array(EXAT, longBytes(ts))
    case GetExExpiry.UnixMilliseconds(ts) => Array(PXAT, longBytes(ts))
    case GetExExpiry.Persist              => Array(PERSIST)

  def hGetExExpiry(expiry: HGetExExpiry): Array[Array[Byte]] = expiry match
    case HGetExExpiry.Seconds(s)           => Array(EX, longBytes(s))
    case HGetExExpiry.Milliseconds(ms)     => Array(PX, longBytes(ms))
    case HGetExExpiry.UnixSeconds(ts)      => Array(EXAT, longBytes(ts))
    case HGetExExpiry.UnixMilliseconds(ts) => Array(PXAT, longBytes(ts))
    case HGetExExpiry.Persist              => Array(PERSIST)

  def expirySet(expiry: ExpirySet): Array[Array[Byte]] = expiry match
    case ExpirySet.Seconds(s)           => Array(EX, longBytes(s))
    case ExpirySet.Milliseconds(ms)     => Array(PX, longBytes(ms))
    case ExpirySet.UnixSeconds(ts)      => Array(EXAT, longBytes(ts))
    case ExpirySet.UnixMilliseconds(ts) => Array(PXAT, longBytes(ts))
    case ExpirySet.Persist              => Array(PERSIST)
    case ExpirySet.KeepExisting         => Array(KEEPTTL)

  def fieldCondition(cond: FieldCondition): Array[Byte] = cond match
    case FieldCondition.OnlyIfAllExist  => FXX
    case FieldCondition.OnlyIfNoneExist => FNX

  def setCondition(cond: SetCondition): Array[Array[Byte]] = cond match
    case SetCondition.OnlyIfNotExists    => Array(NX)
    case SetCondition.OnlyIfExists       => Array(XX)
    case SetCondition.OnlyIfEqualTo(v)   => Array(IFEQ, strBytes(v))

  def setExpiry(expiry: SetExpiry): Array[Array[Byte]] = expiry match
    case SetExpiry.Seconds(s)           => Array(EX, longBytes(s))
    case SetExpiry.Milliseconds(ms)     => Array(PX, longBytes(ms))
    case SetExpiry.UnixSeconds(ts)      => Array(EXAT, longBytes(ts))
    case SetExpiry.UnixMilliseconds(ts) => Array(PXAT, longBytes(ts))
    case SetExpiry.KeepExisting         => Array(KEEPTTL)

  def streamTrimStrategy(strategy: StreamTrimStrategy): Array[Array[Byte]] = strategy match
    case StreamTrimStrategy.MaxLen(threshold, true)  => Array(MAXLEN, longBytes(threshold))
    case StreamTrimStrategy.MaxLen(threshold, false) => Array(MAXLEN, APPROX, longBytes(threshold))
    case StreamTrimStrategy.MinId(id, true)          => Array(MINID, strBytes(id))
    case StreamTrimStrategy.MinId(id, false)         => Array(MINID, APPROX, strBytes(id))

  def geoAddCondition(cond: GeoAddCondition): Array[Array[Byte]] = cond match
    case GeoAddCondition.OnlyIfExists       => Array(XX)
    case GeoAddCondition.OnlyIfDoesNotExist => Array(NX)

  def zAddConditionalChange(cond: ZAddConditionalChange): Array[Array[Byte]] = cond match
    case ZAddConditionalChange.OnlyIfNotExists => Array(NX)
    case ZAddConditionalChange.OnlyIfExists    => Array(XX)

  def zAddUpdateOption(opt: ZAddUpdateOption): Array[Array[Byte]] = opt match
    case ZAddUpdateOption.GreaterThan => Array(GT)
    case ZAddUpdateOption.LessThan   => Array(LT)
