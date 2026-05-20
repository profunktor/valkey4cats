package dev.profunktor.valkey4cats.model

/** AWS service type for IAM authentication */
sealed trait ServiceType

object ServiceType {

  /** AWS ElastiCache service */
  case object ElastiCache extends ServiceType

  /** AWS MemoryDB service */
  case object MemoryDB extends ServiceType
}
