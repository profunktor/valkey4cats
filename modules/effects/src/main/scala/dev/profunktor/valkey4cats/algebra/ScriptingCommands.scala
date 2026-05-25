package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.arguments.FlushMode
import dev.profunktor.valkey4cats.model.ValkeyResponse

/** Server-side Lua/function scripting commands (FCALL, SCRIPT FLUSH, SCRIPT KILL, SCRIPT EXISTS) */
trait ScriptingCommands[F[_], K, V] {

  /** Invoke a server-side function by name.
    *
    * @param function the function name to call
    * @param keys keys accessible to the function
    * @param args additional arguments passed to the function
    */
  def fcall(
      function: K,
      keys: List[K],
      args: List[K]
  ): F[ValkeyResponse[String]]

  /** Invoke a server-side function in read-only mode.
    * The function must not execute write commands.
    *
    * @param function the function name to call
    * @param keys keys accessible to the function
    * @param args additional arguments passed to the function
    */
  def fcallReadOnly(
      function: K,
      keys: List[K],
      args: List[K]
  ): F[ValkeyResponse[String]]

  /** Flush the Lua scripts cache. Uses the default ASYNC mode. */
  def scriptFlush: F[ValkeyResponse[Unit]]

  /** Flush the Lua scripts cache with the specified flush mode. */
  def scriptFlush(mode: FlushMode): F[ValkeyResponse[Unit]]

  /** Kill the currently executing Lua script (if any). */
  def scriptKill: F[ValkeyResponse[Unit]]

  /** Check if scripts exist in the script cache by their SHA1 digests.
    *
    * @param sha1s one or more SHA1 hashes of scripts to check
    * @return list of booleans indicating existence for each script in order
    */
  def scriptExists(sha1s: String*): F[ValkeyResponse[List[Boolean]]]
}
