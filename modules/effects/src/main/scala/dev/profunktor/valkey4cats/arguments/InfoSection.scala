package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.{InfoOptions => GlideInfoOptions}

/** Information sections available from the INFO command */
sealed trait InfoSection

object InfoSection {

  /** Server general information */
  case object Server extends InfoSection

  /** Client connections section */
  case object Clients extends InfoSection

  /** Memory consumption information */
  case object Memory extends InfoSection

  /** RDB and AOF persistence information */
  case object Persistence extends InfoSection

  /** General statistics */
  case object Stats extends InfoSection

  /** Master/replica replication information */
  case object Replication extends InfoSection

  /** CPU consumption statistics */
  case object CPU extends InfoSection

  /** Command statistics */
  case object CommandStats extends InfoSection

  /** Latency statistics */
  case object LatencyStats extends InfoSection

  /** Sentinel information (if applicable) */
  case object Sentinel extends InfoSection

  /** Cluster information */
  case object Cluster extends InfoSection

  /** Modules information */
  case object Modules extends InfoSection

  /** Database keyspace information */
  case object Keyspace extends InfoSection

  /** Error statistics */
  case object ErrorStats extends InfoSection

  /** All sections (excludes module generated sections) */
  case object All extends InfoSection

  /** Default sections */
  case object Default extends InfoSection

  /** Everything including module sections */
  case object Everything extends InfoSection

  /** Convert to Glide InfoOptions.Section */
  def toGlide(section: InfoSection): GlideInfoOptions.Section = section match {
    case Server       => GlideInfoOptions.Section.SERVER
    case Clients      => GlideInfoOptions.Section.CLIENTS
    case Memory       => GlideInfoOptions.Section.MEMORY
    case Persistence  => GlideInfoOptions.Section.PERSISTENCE
    case Stats        => GlideInfoOptions.Section.STATS
    case Replication  => GlideInfoOptions.Section.REPLICATION
    case CPU          => GlideInfoOptions.Section.CPU
    case CommandStats => GlideInfoOptions.Section.COMMANDSTATS
    case LatencyStats => GlideInfoOptions.Section.LATENCYSTATS
    case Sentinel     => GlideInfoOptions.Section.SENTINEL
    case Cluster      => GlideInfoOptions.Section.CLUSTER
    case Modules      => GlideInfoOptions.Section.MODULES
    case Keyspace     => GlideInfoOptions.Section.KEYSPACE
    case ErrorStats   => GlideInfoOptions.Section.ERRORSTATS
    case All          => GlideInfoOptions.Section.ALL
    case Default      => GlideInfoOptions.Section.DEFAULT
    case Everything   => GlideInfoOptions.Section.EVERYTHING
  }

  /** Convert a set of sections to Glide array */
  def toGlideArray(
      sections: Set[InfoSection]
  ): Array[GlideInfoOptions.Section] =
    sections.map(toGlide).toArray
}
