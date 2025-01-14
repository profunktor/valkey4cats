package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.model.ValkeyResponse

/** Probabilistic cardinality estimation commands (PFADD, PFCOUNT, PFMERGE) */
trait HyperLogLogCommands[F[_], K, V] {

  /** Add the specified elements to the HyperLogLog at key.
    *
    * @param key The key of the HyperLogLog
    * @param elements Elements to add
    * @return true if the internal representation was altered, false otherwise
    */
  def pfadd(key: K, elements: V*): F[ValkeyResponse[Boolean]]

  /** Return the approximated cardinality of the set(s) observed by the HyperLogLog(s) at the specified key(s).
    *
    * @param keys The keys of the HyperLogLog structures
    * @return The approximated number of unique elements
    */
  def pfcount(keys: K*): F[ValkeyResponse[Long]]

  /** Merge multiple HyperLogLog values into a single one.
    * The merged HyperLogLog is stored at destkey.
    *
    * @param destkey The destination key
    * @param sourcekeys The source HyperLogLog keys
    * @return Unit on success
    */
  def pfmerge(destkey: K, sourcekeys: K*): F[ValkeyResponse[Unit]]
}
