package dev.profunktor.valkey4cats.model

import glide.api.models.configuration.{EvictionPolicy => GlideEvictionPolicy}

sealed trait CacheEvictionPolicy {
  private[valkey4cats] def toGlide: GlideEvictionPolicy
}

object CacheEvictionPolicy {
  case object LRU extends CacheEvictionPolicy {
    private[valkey4cats] def toGlide: GlideEvictionPolicy =
      GlideEvictionPolicy.LRU
  }
  case object LFU extends CacheEvictionPolicy {
    private[valkey4cats] def toGlide: GlideEvictionPolicy =
      GlideEvictionPolicy.LFU
  }
}
