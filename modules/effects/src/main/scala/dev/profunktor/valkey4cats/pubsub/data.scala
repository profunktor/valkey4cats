package dev.profunktor.valkey4cats.pubsub

opaque type ValkeyChannel[K] = K
object ValkeyChannel {
  def apply[K](value: K): ValkeyChannel[K] = value
  extension [K](ch: ValkeyChannel[K]) def underlying: K = ch
}

opaque type ValkeyPattern[K] = K
object ValkeyPattern {
  def apply[K](value: K): ValkeyPattern[K] = value
  extension [K](p: ValkeyPattern[K]) def underlying: K = p
}

final case class ValkeyPatternEvent[K, V](
    pattern: ValkeyPattern[K],
    channel: ValkeyChannel[K],
    message: V
)
