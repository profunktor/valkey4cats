package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.algebra.*

/** Composite trait that provides access to all Valkey command algebras.
  *
  * Not designed for external implementation — use the constructors in [[Valkey]].
  */
trait ValkeyCommands[F[_], K, V]
    extends StringCommands[F, K, V]
    with KeyCommands[F, K, V]
    with HashCommands[F, K, V]
    with ListCommands[F, K, V]
    with SetCommands[F, K, V]
    with SortedSetCommands[F, K, V]
    with HyperLogLogCommands[F, K, V]
    with GeoCommands[F, K, V]
    with BitmapCommands[F, K, V]
    with PubSubCommands[F, K, V]
    with StreamCommands[F, K, V]
    with ScriptingCommands[F, K, V]
    with ServerCommands[F, K, V]
    with ConnectionCommands[F, K, V]
    with CacheMetrics[F]
