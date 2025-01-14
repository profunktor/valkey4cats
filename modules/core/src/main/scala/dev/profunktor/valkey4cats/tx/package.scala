package dev.profunktor.valkey4cats

import scala.util.control.NoStackTrace

package object tx {
  case object PipelineError extends NoStackTrace
  case object TransactionDiscarded extends NoStackTrace
}
