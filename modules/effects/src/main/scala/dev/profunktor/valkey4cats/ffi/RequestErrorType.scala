package dev.profunktor.valkey4cats.ffi

private[valkey4cats] enum RequestErrorType(val code: Int):
  case Unspecified extends RequestErrorType(0)
  case ExecAbort  extends RequestErrorType(1)
  case Timeout    extends RequestErrorType(2)
  case Disconnect extends RequestErrorType(3)

private[valkey4cats] object RequestErrorType:
  def fromCode(n: Int): RequestErrorType =
    n match
      case 0 => Unspecified
      case 1 => ExecAbort
      case 2 => Timeout
      case 3 => Disconnect
      case _ => Unspecified
