package dev.profunktor.valkey4cats.results

/** Result of a SCAN operation, containing the next cursor position and the scanned values. */
final case class ScanResult[A](cursor: String, values: A)

/** Result of a cluster-aware SCAN operation, containing a cluster cursor and the scanned values. */
final case class ClusterScanResult[A](
    cursor: ClusterScanCursor,
    values: A
)

/** Opaque cursor for iterating over keys in a Valkey cluster. */
sealed trait ClusterScanCursor {
  def isFinished: Boolean
}

object ClusterScanCursor {
  def initial: ClusterScanCursor = Impl(false)

  private[valkey4cats] def apply(finished: Boolean): ClusterScanCursor =
    Impl(finished)

  private final case class Impl(isFinished: Boolean) extends ClusterScanCursor
}
