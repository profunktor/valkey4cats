package dev.profunktor.valkey4cats.model

/** Strategy for read operations in cluster/replication setups */
sealed trait ReadFromStrategy

object ReadFromStrategy {

  /** Read from primary only (default) */
  case object Primary extends ReadFromStrategy

  /** Prefer reading from replicas, fallback to primary */
  case object PreferReplica extends ReadFromStrategy

  /** AZ Affinity: prefer reading from replicas in the same availability zone
    * This is a Glide-specific feature for optimal latency in cloud deployments.
    * Falls back to other replicas or primary if no local replicas are available.
    */
  case object AzAffinity extends ReadFromStrategy

  /** AZ Affinity with Replicas and Primary: spread read requests among nodes
    * within the client's availability zone in a round robin manner.
    * Prioritizes local replicas, then the local primary, and falls back to
    * other replicas or primary in other zones if needed.
    * Requires Valkey 8.0+.
    */
  case object AzAffinityReplicasAndPrimary extends ReadFromStrategy

}
