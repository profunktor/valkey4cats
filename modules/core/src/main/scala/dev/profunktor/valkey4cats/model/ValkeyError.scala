package dev.profunktor.valkey4cats.model

/** Typed representation of Valkey server errors, parsed from error response prefixes. */
sealed trait ValkeyError {
  def message: String
}

object ValkeyError {

  /** Operation attempted against a key holding the wrong data type. */
  case class WrongType(message: String) extends ValkeyError

  /** General command execution failure (ERR prefix). */
  case class CommandError(message: String) extends ValkeyError

  /** Write operation attempted against a read-only replica. */
  case class ReadOnly(message: String) extends ValkeyError

  /** Multi-key operation where keys hash to different cluster slots. */
  case class CrossSlot(message: String) extends ValkeyError

  /** Server has reached its configured memory limit. */
  case class OutOfMemory(message: String) extends ValkeyError

  /** Authentication failure (invalid password or missing credentials). */
  case class AuthError(message: String) extends ValkeyError

  /** Referenced Lua script not found in the script cache. */
  case class NoScript(message: String) extends ValkeyError

  /** Server is busy (e.g. a Lua script is still executing). */
  case class Busy(message: String) extends ValkeyError

  /** EXEC failed because a watched key was modified. */
  case class TransactionAborted(message: String) extends ValkeyError

  /** Unrecognized server error that does not match any known prefix. */
  case class Unexpected(message: String, cause: Option[Throwable] = None)
      extends ValkeyError

  def fromMessage(msg: String): ValkeyError = {
    val m = if (msg == null) "" else msg
    m match {
      case s if s.startsWith("WRONGTYPE") => WrongType(s)
      case s if s.startsWith("READONLY")  => ReadOnly(s)
      case s if s.startsWith("CROSSSLOT") => CrossSlot(s)
      case s if s.startsWith("OOM")       => OutOfMemory(s)
      case s if s.startsWith("NOAUTH")    => AuthError(s)
      case s if s.startsWith("WRONGPASS") => AuthError(s)
      case s if s.startsWith("NOSCRIPT")  => NoScript(s)
      case s if s.startsWith("BUSY")      => Busy(s)
      case s if s.startsWith("ERR")       => CommandError(s)
      case s                              => Unexpected(s)
    }
  }
}
