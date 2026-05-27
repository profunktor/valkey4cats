package dev.profunktor.valkey4cats.examples

import scala.concurrent.duration.*
import cats.effect.*
import dev.profunktor.valkey4cats.connection.ValkeyClient
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.pubsub.*
import fs2.Stream

/** Pub/Sub example for Valkey4S
  *
  * To run this example, ensure you have Valkey or Redis running on localhost:6379
  *
  * Demonstrates:
  *   - Channel subscription (subscribe/publish)
  *   - Pattern subscription (psubscribe with wildcards)
  *   - Stream-based message handling with fs2
  *   - Concurrent publishers and subscribers
  */
object PubSubExample extends IOApp.Simple {

  implicit val logger: Log[IO] = Log.Stdout.instance[IO]

  def run: IO[Unit] = {
    val resources = for {
      client <- ValkeyClient[IO].from("redis://localhost:6379")
      pubsub <- ValkeyPubSub.utf8[IO](client)
    } yield pubsub

    resources.use { pubsub =>
      // Channel subscription demo
      val channel = ValkeyChannel("notifications")

      val subscriber: Stream[IO, Unit] =
        pubsub.subscribe(channel)
          .evalMap(msg => IO.println(s"[subscriber] Received: $msg"))
          .take(5)

      val publisher: Stream[IO, Unit] =
        Stream.sleep[IO](500.millis) >>
          Stream.emits(List("hello", "world", "foo", "bar", "done"))
            .metered(200.millis)
            .evalMap(msg => pubsub.publish(channel, msg) >> IO.println(s"[publisher] Sent: $msg"))

      // Pattern subscription demo
      val pattern = ValkeyPattern("events.*")

      val patternSubscriber: Stream[IO, Unit] =
        pubsub.psubscribe(pattern)
          .evalMap(evt => IO.println(s"[pattern] ${evt.pattern.underlying} matched ${evt.channel.underlying}: ${evt.message}"))
          .take(3)

      val patternPublisher: Stream[IO, Unit] =
        Stream.sleep[IO](500.millis) >>
          Stream.emits(List(
            ValkeyChannel("events.click") -> "button-1",
            ValkeyChannel("events.scroll") -> "page-down",
            ValkeyChannel("events.hover") -> "menu-item"
          ))
            .metered(300.millis)
            .evalMap { case (ch, msg) => pubsub.publish(ch, msg).void }

      IO.println("=== Channel Subscription ===") >>
        subscriber.concurrently(publisher).compile.drain >>
        IO.println("\n=== Pattern Subscription ===") >>
        patternSubscriber.concurrently(patternPublisher).compile.drain >>
        IO.println("\n=== Done! ===")
    }
  }
}
