package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands as G
import dev.profunktor.valkey4cats.arguments.GetExExpiry.{
  Milliseconds,
  Persist,
  Seconds,
  UnixMilliseconds,
  UnixSeconds
}

/** Expiry options for GETEX command */
sealed trait GetExExpiry { self =>

  private[valkey4cats] def toGlide: G.GetExOptions =
    self match {
      case Seconds(s)           => G.GetExOptions.Seconds(s)
      case Milliseconds(ms)     => G.GetExOptions.Milliseconds(ms)
      case UnixSeconds(ts)      => G.GetExOptions.UnixSeconds(ts)
      case UnixMilliseconds(ts) => G.GetExOptions.UnixMilliseconds(ts)
      case Persist              => G.GetExOptions.Persist()
    }
}

object GetExExpiry {

  /** Set expiry in seconds */
  final case class Seconds(seconds: Long) extends GetExExpiry

  /** Set expiry in milliseconds */
  final case class Milliseconds(milliseconds: Long) extends GetExExpiry

  /** Set expiry as Unix timestamp in seconds */
  final case class UnixSeconds(timestamp: Long) extends GetExExpiry

  /** Set expiry as Unix timestamp in milliseconds */
  final case class UnixMilliseconds(timestamp: Long) extends GetExExpiry

  /** Remove the time to live associated with the key (PERSIST) */
  case object Persist extends GetExExpiry

}
