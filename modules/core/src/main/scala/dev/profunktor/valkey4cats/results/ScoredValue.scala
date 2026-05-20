package dev.profunktor.valkey4cats.results

final case class ScoredValue[V](value: V, score: Double)
