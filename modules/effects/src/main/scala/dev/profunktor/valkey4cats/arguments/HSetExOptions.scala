package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.{
  ExpirySet => GlideExpirySet,
  FieldConditionalChange
}

/** Expiry options for the HSETEX command */
sealed trait ExpirySet { self =>
  private[valkey4cats] def toGlide: GlideExpirySet =
    self match {
      case ExpirySet.Seconds(s)           => GlideExpirySet.Seconds(s)
      case ExpirySet.Milliseconds(ms)     => GlideExpirySet.Milliseconds(ms)
      case ExpirySet.UnixSeconds(ts)      => GlideExpirySet.UnixSeconds(ts)
      case ExpirySet.UnixMilliseconds(ts) => GlideExpirySet.UnixMilliseconds(ts)
      case ExpirySet.Persist              => GlideExpirySet.Persist()
      case ExpirySet.KeepExisting         => GlideExpirySet.KeepExisting()
    }
}

object ExpirySet {

  /** Set field expiry in seconds */
  case class Seconds(value: Long) extends ExpirySet

  /** Set field expiry in milliseconds */
  case class Milliseconds(value: Long) extends ExpirySet

  /** Set field expiry as Unix timestamp in seconds */
  case class UnixSeconds(value: Long) extends ExpirySet

  /** Set field expiry as Unix timestamp in milliseconds */
  case class UnixMilliseconds(value: Long) extends ExpirySet

  /** Remove the existing TTL on the field */
  case object Persist extends ExpirySet

  /** Retain the existing TTL on the field */
  case object KeepExisting extends ExpirySet
}

/** Conditional mode for HSETEX field operations */
sealed trait FieldCondition { self =>
  private[valkey4cats] def toGlide: FieldConditionalChange =
    self match {
      case FieldCondition.OnlyIfAllExist =>
        FieldConditionalChange.ONLY_IF_ALL_EXIST
      case FieldCondition.OnlyIfNoneExist =>
        FieldConditionalChange.ONLY_IF_NONE_EXIST
    }
}

object FieldCondition {

  /** Only set fields if all specified fields already exist */
  case object OnlyIfAllExist extends FieldCondition

  /** Only set fields if none of the specified fields exist */
  case object OnlyIfNoneExist extends FieldCondition
}
