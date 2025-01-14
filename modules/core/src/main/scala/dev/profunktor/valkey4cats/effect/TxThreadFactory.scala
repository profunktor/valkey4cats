package dev.profunktor.valkey4cats.effect

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.ThreadFactory

private[valkey4cats] object TxThreadFactory extends ThreadFactory {
  override def newThread(r: Runnable): Thread =
    this.synchronized {
      val t: Thread = new Thread(r)
      val f = DateTimeFormatter.ofPattern("Hmd.S")
      val now = Instant.now().atOffset(ZoneOffset.UTC)
      val time = f.format(now)
      t.setName(s"valkey-tx-ec-$time")
      t
    }
}
