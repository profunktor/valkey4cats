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

  /** Returns true when the scan has visited all slots and iteration is complete. */
  def isFinished: Boolean
}

object ClusterScanCursor {
  private[valkey4cats] def initial: ClusterScanCursor =
    Wrapped(
      glide.api.models.commands.scan.ClusterScanCursor.initialCursor()
    )

  private[valkey4cats] final case class Wrapped(
      underlying: glide.api.models.commands.scan.ClusterScanCursor
  ) extends ClusterScanCursor {
    def isFinished: Boolean = underlying.isFinished
  }
}
