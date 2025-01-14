package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.arguments.FlushMode
import dev.profunktor.valkey4cats.model.ValkeyResponse

/** Server-side Lua/function scripting commands (FCALL, SCRIPT FLUSH, SCRIPT KILL) */
trait ScriptingCommands[F[_], K, V] {

  def fcall(
      function: K,
      keys: List[K],
      args: List[K]
  ): F[ValkeyResponse[String]]

  def fcallReadOnly(
      function: K,
      keys: List[K],
      args: List[K]
  ): F[ValkeyResponse[String]]

  def scriptFlush: F[ValkeyResponse[Unit]]

  def scriptFlush(mode: FlushMode): F[ValkeyResponse[Unit]]

  def scriptKill: F[ValkeyResponse[Unit]]

  def scriptExists(sha1s: String*): F[ValkeyResponse[List[Boolean]]]
}
