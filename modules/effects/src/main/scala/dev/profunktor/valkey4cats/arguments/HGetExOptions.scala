package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.{
  HGetExExpiry => GlideHGetExExpiry,
  HGetExOptions => GlideHGetExOptions
}

/** Expiry options for the HGETEX command */
sealed trait HGetExExpiry { self =>
  private[valkey4cats] def toGlide: GlideHGetExOptions =
    GlideHGetExOptions
      .builder()
      .expiry(self match {
        case HGetExExpiry.Seconds(s)       => GlideHGetExExpiry.Seconds(s)
        case HGetExExpiry.Milliseconds(ms) => GlideHGetExExpiry.Milliseconds(ms)
        case HGetExExpiry.UnixSeconds(ts)  => GlideHGetExExpiry.UnixSeconds(ts)
        case HGetExExpiry.UnixMilliseconds(ts) =>
          GlideHGetExExpiry.UnixMilliseconds(ts)
        case HGetExExpiry.Persist => GlideHGetExExpiry.Persist()
      })
      .build()
}

object HGetExExpiry {

  /** Set field expiry in seconds */
  case class Seconds(value: Long) extends HGetExExpiry

  /** Set field expiry in milliseconds */
  case class Milliseconds(value: Long) extends HGetExExpiry

  /** Set field expiry as Unix timestamp in seconds */
  case class UnixSeconds(value: Long) extends HGetExExpiry

  /** Set field expiry as Unix timestamp in milliseconds */
  case class UnixMilliseconds(value: Long) extends HGetExExpiry

  /** Remove the existing TTL on the field */
  case object Persist extends HGetExExpiry
}
