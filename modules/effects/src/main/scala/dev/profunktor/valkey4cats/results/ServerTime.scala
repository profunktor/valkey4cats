package dev.profunktor.valkey4cats.results

/** Server time as returned by the TIME command, split into seconds and microseconds. */
final case class ServerTime(unixSeconds: Long, microseconds: Long)
