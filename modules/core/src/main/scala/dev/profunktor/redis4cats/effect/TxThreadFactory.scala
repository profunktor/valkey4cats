/*
 * Copyright 2018-2025 ProfunKtor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.profunktor.redis4cats.effect

import java.time.{ Instant, ZoneOffset }
import java.time.format.DateTimeFormatter
import java.util.concurrent.ThreadFactory

private[redis4cats] object TxThreadFactory extends ThreadFactory {
  override def newThread(r: Runnable): Thread =
    this.synchronized {
      val t: Thread = new Thread(r)
      val f         = DateTimeFormatter.ofPattern("Hmd.S")
      val now       = Instant.now().atOffset(ZoneOffset.UTC)
      val time      = f.format(now)
      t.setName(s"redis-tx-ec-$time")
      return t
    }
}
