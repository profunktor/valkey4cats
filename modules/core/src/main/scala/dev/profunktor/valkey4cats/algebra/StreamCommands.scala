package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.arguments.{
  StreamRangeBound,
  StreamTrimStrategy
}
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.results.{
  AutoClaimIdResult,
  AutoClaimResult,
  PendingEntry,
  PendingSummary
}

/** Valkey Streams commands (XADD, XREAD, XRANGE, consumer groups) */
trait StreamCommands[F[_], K, V] {

  def xadd(key: K, fieldValues: Map[K, V]): F[ValkeyResponse[String]]

  def xlen(key: K): F[ValkeyResponse[Long]]

  def xdel(key: K, ids: String*): F[ValkeyResponse[Long]]

  def xtrim(key: K, strategy: StreamTrimStrategy): F[ValkeyResponse[Long]]

  def xrange(
      key: K,
      start: StreamRangeBound,
      end: StreamRangeBound
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]]

  def xrange(
      key: K,
      start: StreamRangeBound,
      end: StreamRangeBound,
      count: Long
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]]

  def xrevrange(
      key: K,
      end: StreamRangeBound,
      start: StreamRangeBound
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]]

  def xrevrange(
      key: K,
      end: StreamRangeBound,
      start: StreamRangeBound,
      count: Long
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]]

  def xgroupCreate(
      key: K,
      group: K,
      id: String
  ): F[ValkeyResponse[Unit]]

  def xgroupCreate(
      key: K,
      group: K,
      id: String,
      mkStream: Boolean
  ): F[ValkeyResponse[Unit]]

  def xgroupDestroy(key: K, group: K): F[ValkeyResponse[Boolean]]

  def xgroupCreateConsumer(
      key: K,
      group: K,
      consumer: K
  ): F[ValkeyResponse[Boolean]]

  def xgroupDelConsumer(
      key: K,
      group: K,
      consumer: K
  ): F[ValkeyResponse[Long]]

  def xgroupSetId(key: K, group: K, id: String): F[ValkeyResponse[Unit]]

  def xack(key: K, group: K, ids: String*): F[ValkeyResponse[Long]]

  def xread(
      keysAndIds: Map[K, String]
  ): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]]

  def xread(
      keysAndIds: Map[K, String],
      count: Long,
      block: Long
  ): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]]

  def xreadgroup(
      group: K,
      consumer: K,
      keysAndIds: Map[K, String]
  ): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]]

  def xreadgroup(
      group: K,
      consumer: K,
      keysAndIds: Map[K, String],
      count: Long,
      block: Long
  ): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]]

  def xreadgroup(
      group: K,
      consumer: K,
      keysAndIds: Map[K, String],
      count: Long,
      block: Long,
      noAck: Boolean
  ): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]]

  /** Claim ownership of pending stream messages.
    *
    * @param key The stream key
    * @param group The consumer group name
    * @param consumer The consumer claiming the messages
    * @param minIdleTimeMillis Only claim messages idle for at least this many milliseconds
    * @param ids The message IDs to claim
    * @return Map of claimed message IDs to their field-value pairs
    */
  def xclaim(
      key: K,
      group: K,
      consumer: K,
      minIdleTimeMillis: Long,
      ids: String*
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]]

  /** Get summary information about pending messages in a consumer group.
    *
    * @param key The stream key
    * @param group The consumer group name
    * @return (pendingCount, smallestId, greatestId, List of (consumer, count) pairs)
    */
  def xpendingSummary(
      key: K,
      group: K
  ): F[ValkeyResponse[PendingSummary[K]]]

  /** Get detailed information about pending messages in a consumer group.
    *
    * @param key The stream key
    * @param group The consumer group name
    * @param start Start of range
    * @param end End of range
    * @param count Maximum number of entries to return
    * @return List of pending entry details
    */
  def xpendingRange(
      key: K,
      group: K,
      start: StreamRangeBound,
      end: StreamRangeBound,
      count: Long
  ): F[ValkeyResponse[List[PendingEntry[K]]]]

  /** Automatically claim pending messages that have been idle for at least minIdleTimeMillis.
    *
    * @param key The stream key
    * @param group The consumer group name
    * @param consumer The consumer claiming the messages
    * @param minIdleTimeMillis Minimum idle time in milliseconds
    * @param start Start stream ID to scan from ("0-0" to start from beginning)
    * @return AutoClaimResult with nextCursor, claimed entries, and deleted IDs
    */
  def xautoclaim(
      key: K,
      group: K,
      consumer: K,
      minIdleTimeMillis: Long,
      start: String
  ): F[ValkeyResponse[AutoClaimResult[K, V]]]

  /** Automatically claim pending messages with a count limit.
    *
    * @param key The stream key
    * @param group The consumer group name
    * @param consumer The consumer claiming the messages
    * @param minIdleTimeMillis Minimum idle time in milliseconds
    * @param start Start stream ID to scan from
    * @param count Maximum number of messages to claim
    * @return AutoClaimResult with nextCursor, claimed entries, and deleted IDs
    */
  def xautoclaim(
      key: K,
      group: K,
      consumer: K,
      minIdleTimeMillis: Long,
      start: String,
      count: Long
  ): F[ValkeyResponse[AutoClaimResult[K, V]]]

  /** Like xautoclaim but returns only the message IDs, not the full entries.
    *
    * @param key The stream key
    * @param group The consumer group name
    * @param consumer The consumer claiming the messages
    * @param minIdleTimeMillis Minimum idle time in milliseconds
    * @param start Start stream ID to scan from
    * @return AutoClaimIdResult with nextCursor, claimed IDs, and deleted IDs
    */
  def xautoclaimJustId(
      key: K,
      group: K,
      consumer: K,
      minIdleTimeMillis: Long,
      start: String
  ): F[ValkeyResponse[AutoClaimIdResult]]
}
