package dev.profunktor.valkey4cats.results

/** Summary of pending entries for a stream consumer group, as returned by XPENDING (summary form). */
final case class PendingSummary[K](
    pendingCount: Long,
    smallestId: Option[String],
    greatestId: Option[String],
    consumers: List[PendingSummary.ConsumerPending[K]]
)

object PendingSummary {

  /** Per-consumer count of pending messages within a consumer group. */
  final case class ConsumerPending[K](consumer: K, pendingCount: Long)
}

/** A single pending entry for a stream consumer, as returned by XPENDING (detail form). */
final case class PendingEntry[K](
    messageId: String,
    consumer: K,
    idleTimeMillis: Long,
    deliveryCount: Long
)

/** Result of XAUTOCLAIM containing claimed entries with their field/value pairs, plus deleted message IDs. */
final case class AutoClaimResult[K, V](
    nextCursor: String,
    claimedEntries: Map[String, List[(K, V)]],
    deletedIds: List[String]
)

/** Result of XAUTOCLAIM with JUSTID option, containing only claimed and deleted message IDs. */
final case class AutoClaimIdResult(
    nextCursor: String,
    claimedIds: List[String],
    deletedIds: List[String]
)
