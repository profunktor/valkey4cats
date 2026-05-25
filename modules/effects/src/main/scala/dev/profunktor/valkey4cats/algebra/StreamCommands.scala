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

  /** Append an entry to a stream. Returns the auto-generated entry ID.
    *
    * @param key the stream key
    * @param fieldValues map of field-value pairs for the new entry
    */
  def xadd(key: K, fieldValues: Map[K, V]): F[ValkeyResponse[String]]

  /** Return the number of entries in a stream. */
  def xlen(key: K): F[ValkeyResponse[Long]]

  /** Delete entries from a stream by their IDs.
    *
    * @return the number of entries actually deleted (missing IDs are ignored)
    */
  def xdel(key: K, ids: String*): F[ValkeyResponse[Long]]

  /** Trim a stream to a given length or minimum ID.
    *
    * @param strategy MAXLEN or MINID with optional approximate (~) trimming
    * @return the number of entries removed
    */
  def xtrim(key: K, strategy: StreamTrimStrategy): F[ValkeyResponse[Long]]

  /** Return entries in a stream within the given ID range (inclusive).
    *
    * @param start lower bound (use `StreamRangeBound.Minimum` for the beginning)
    * @param end upper bound (use `StreamRangeBound.Maximum` for the end)
    */
  def xrange(
      key: K,
      start: StreamRangeBound,
      end: StreamRangeBound
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]]

  /** Return at most `count` entries in a stream within the given ID range. */
  def xrange(
      key: K,
      start: StreamRangeBound,
      end: StreamRangeBound,
      count: Long
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]]

  /** Return entries in reverse order (highest to lowest ID).
    *
    * @param end upper bound (note: first parameter despite being "end")
    * @param start lower bound
    */
  def xrevrange(
      key: K,
      end: StreamRangeBound,
      start: StreamRangeBound
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]]

  /** Return at most `count` entries in reverse order. */
  def xrevrange(
      key: K,
      end: StreamRangeBound,
      start: StreamRangeBound,
      count: Long
  ): F[ValkeyResponse[Map[String, List[(K, V)]]]]

  /** Create a consumer group for the given stream.
    *
    * @param key the stream key
    * @param group name of the consumer group to create
    * @param id starting entry ID ("$" for new entries only, "0" for all existing)
    */
  def xgroupCreate(
      key: K,
      group: K,
      id: String
  ): F[ValkeyResponse[Unit]]

  /** Create a consumer group, optionally creating the stream if it doesn't exist.
    *
    * @param mkStream if true, create the stream if it doesn't exist (MKSTREAM option)
    */
  def xgroupCreate(
      key: K,
      group: K,
      id: String,
      mkStream: Boolean
  ): F[ValkeyResponse[Unit]]

  /** Destroy a consumer group. Returns true if the group existed. */
  def xgroupDestroy(key: K, group: K): F[ValkeyResponse[Boolean]]

  /** Create a consumer in a group. Returns true if the consumer was newly created. */
  def xgroupCreateConsumer(
      key: K,
      group: K,
      consumer: K
  ): F[ValkeyResponse[Boolean]]

  /** Delete a consumer from a group.
    *
    * @return the number of pending messages that the consumer had before deletion
    */
  def xgroupDelConsumer(
      key: K,
      group: K,
      consumer: K
  ): F[ValkeyResponse[Long]]

  /** Set the last-delivered ID of a consumer group. */
  def xgroupSetId(key: K, group: K, id: String): F[ValkeyResponse[Unit]]

  /** Acknowledge one or more messages as processed by a consumer group.
    *
    * @return the number of messages successfully acknowledged
    */
  def xack(key: K, group: K, ids: String*): F[ValkeyResponse[Long]]

  /** Read entries from one or more streams starting from the given IDs.
    * Returns None if no new entries are available.
    *
    * @param keysAndIds map of stream key to last-seen entry ID (use "0" for all, "$" for new only)
    */
  def xread(
      keysAndIds: Map[K, String]
  ): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]]

  /** Read entries with count limit and optional blocking.
    *
    * @param count maximum number of entries to return per stream
    * @param block block for this many milliseconds (0 = block indefinitely, negative = don't block)
    */
  def xread(
      keysAndIds: Map[K, String],
      count: Long,
      block: Long
  ): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]]

  /** Read entries as a consumer group member.
    * Messages delivered to this consumer must be acknowledged with `xack`.
    *
    * @param group the consumer group name
    * @param consumer the consumer name within the group
    * @param keysAndIds map of stream key to last-seen ID (use ">" for new undelivered messages)
    */
  def xreadgroup(
      group: K,
      consumer: K,
      keysAndIds: Map[K, String]
  ): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]]

  /** Read entries as a consumer group member with count limit and blocking. */
  def xreadgroup(
      group: K,
      consumer: K,
      keysAndIds: Map[K, String],
      count: Long,
      block: Long
  ): F[ValkeyResponse[Option[Map[K, Map[String, List[(K, V)]]]]]]

  /** Read entries as a consumer group member with count, blocking, and noAck options.
    *
    * @param noAck if true, messages are not added to the pending entries list (no ack required)
    */
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
