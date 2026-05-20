package dev.profunktor.valkey4cats.model

/** Redis/Valkey protocol version */
sealed trait ProtocolVersion

object ProtocolVersion {

  /** RESP2 protocol (older, compatible with all Redis versions) */
  case object RESP2 extends ProtocolVersion

  /** RESP3 protocol (Redis 6.0+/Valkey, better performance and features) */
  case object RESP3 extends ProtocolVersion
}
