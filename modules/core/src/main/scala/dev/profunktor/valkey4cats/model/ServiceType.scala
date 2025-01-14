package dev.profunktor.valkey4cats.model

import glide.api.models.{configuration => G}

/** AWS service type for IAM authentication */
sealed trait ServiceType { self =>
  private[valkey4cats] def toGlide: G.ServiceType =
    self match {
      case ServiceType.ElastiCache => G.ServiceType.ELASTICACHE
      case ServiceType.MemoryDB    => G.ServiceType.MEMORYDB
    }
}

object ServiceType {

  /** AWS ElastiCache service */
  case object ElastiCache extends ServiceType

  /** AWS MemoryDB service */
  case object MemoryDB extends ServiceType
}
