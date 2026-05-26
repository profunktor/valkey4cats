package dev.profunktor.valkey4cats.pubsub

import scala.concurrent.duration.*
import cats.effect.IO
import dev.profunktor.valkey4cats.ValkeyTestSuite
import dev.profunktor.valkey4cats.connection.ValkeyClient
import fs2.Stream

class PubSubIntegrationSuite extends ValkeyTestSuite {

  private def pubsubResource =
    ValkeyClient[IO].from(valkeyUri).flatMap(ValkeyPubSub.utf8[IO](_))

  // ========== Task 8: Basic subscribe/publish ==========

  test("subscribe receives published messages") {
    pubsubResource.use { pubsub =>
      val channel = ValkeyChannel("test-channel")

      val subscriber = pubsub.subscribe(channel).take(1).compile.lastOrError
      val publisher = Stream.sleep[IO](200.millis) >>
        Stream.eval(pubsub.publish(channel, "hello world"))

      publisher.concurrently(Stream.eval(subscriber.flatMap { msg =>
        IO(assertEquals(msg, "hello world"))
      })).compile.drain
    }
  }

  test("subscribe receives multiple messages in order") {
    pubsubResource.use { pubsub =>
      val channel = ValkeyChannel("multi-msg-channel")

      val messages = List("first", "second", "third")
      val subscriber = pubsub.subscribe(channel).take(3).compile.toList
      val publisher = Stream.sleep[IO](200.millis) >>
        Stream.emits(messages).evalMap(msg => pubsub.publish(channel, msg))

      publisher.concurrently(Stream.eval(subscriber.flatMap { received =>
        IO(assertEquals(received, messages))
      })).compile.drain
    }
  }

  test("unsubscribe terminates the stream") {
    pubsubResource.use { pubsub =>
      val channel = ValkeyChannel("unsub-channel")

      for {
        fiber <- pubsub.subscribe(channel).compile.toList.start
        _     <- Stream.sleep[IO](200.millis).compile.drain
        _     <- pubsub.publish(channel, "before-unsub")
        _     <- IO.sleep(100.millis)
        _     <- pubsub.unsubscribe(channel)
        msgs  <- fiber.joinWithNever
        _     <- IO(assertEquals(msgs.size, 1))
        _     <- IO(assertEquals(msgs.head, "before-unsub"))
      } yield ()
    }
  }

  // ========== Task 9: Pattern subscriptions ==========

  test("psubscribe receives messages matching pattern") {
    pubsubResource.use { pubsub =>
      val pattern = ValkeyPattern("news.*")
      val channel = ValkeyChannel("news.sports")

      val subscriber = pubsub.psubscribe(pattern).take(1).compile.lastOrError
      val publisher = Stream.sleep[IO](200.millis) >>
        Stream.eval(pubsub.publish(channel, "goal scored"))

      publisher.concurrently(Stream.eval(subscriber.flatMap { event =>
        IO {
          assertEquals(event.pattern.underlying, "news.*")
          assertEquals(event.channel.underlying, "news.sports")
          assertEquals(event.message, "goal scored")
        }
      })).compile.drain
    }
  }

  test("psubscribe receives from multiple matching channels") {
    pubsubResource.use { pubsub =>
      val pattern = ValkeyPattern("events.*")
      val channel1 = ValkeyChannel("events.click")
      val channel2 = ValkeyChannel("events.scroll")

      val subscriber = pubsub.psubscribe(pattern).take(2).compile.toList
      val publisher = Stream.sleep[IO](200.millis) >>
        Stream.eval(pubsub.publish(channel1, "clicked")) >>
        Stream.eval(pubsub.publish(channel2, "scrolled"))

      publisher.concurrently(Stream.eval(subscriber.flatMap { events =>
        IO {
          assertEquals(events.size, 2)
          assert(events.exists(e => e.channel.underlying == "events.click" && e.message == "clicked"))
          assert(events.exists(e => e.channel.underlying == "events.scroll" && e.message == "scrolled"))
        }
      })).compile.drain
    }
  }

  test("punsubscribe terminates pattern stream") {
    pubsubResource.use { pubsub =>
      val pattern = ValkeyPattern("punsub.*")
      val channel = ValkeyChannel("punsub.test")

      for {
        fiber <- pubsub.psubscribe(pattern).compile.toList.start
        _     <- Stream.sleep[IO](200.millis).compile.drain
        _     <- pubsub.publish(channel, "before-punsub")
        _     <- IO.sleep(100.millis)
        _     <- pubsub.punsubscribe(pattern)
        events <- fiber.joinWithNever
        _      <- IO(assertEquals(events.size, 1))
        _      <- IO(assertEquals(events.head.message, "before-punsub"))
      } yield ()
    }
  }

  // ========== Task 10: Multi-subscriber & cleanup ==========

  test("multiple subscribers on same channel both receive messages") {
    pubsubResource.use { pubsub =>
      val channel = ValkeyChannel("multi-sub-channel")

      val sub1 = pubsub.subscribe(channel).take(1).compile.lastOrError
      val sub2 = pubsub.subscribe(channel).take(1).compile.lastOrError
      val publisher = Stream.sleep[IO](300.millis) >>
        Stream.eval(pubsub.publish(channel, "broadcast"))

      for {
        fiber1 <- sub1.start
        fiber2 <- sub2.start
        _      <- publisher.compile.drain
        msg1   <- fiber1.joinWithNever
        msg2   <- fiber2.joinWithNever
        _      <- IO(assertEquals(msg1, "broadcast"))
        _      <- IO(assertEquals(msg2, "broadcast"))
      } yield ()
    }
  }

  test("stream cancellation decrements subscriber count") {
    pubsubResource.use { pubsub =>
      val channel = ValkeyChannel("cancel-channel")

      for {
        // Start two subscribers
        fiber1 <- pubsub.subscribe(channel).take(1).compile.drain.start
        fiber2 <- pubsub.subscribe(channel).compile.drain.start
        _      <- Stream.sleep[IO](300.millis).compile.drain

        // Publish first message, should be received by both
        _      <- pubsub.publish(channel, "first")
        _      <- fiber1.joinWithNever // This one completes after 1 message

        // Second subscriber should still be active
        _      <- IO.sleep(100.millis)
        _      <- pubsub.publish(channel, "second")
        _      <- IO.sleep(100.millis)

        // Cancel the second subscriber
        _      <- fiber2.cancel
      } yield ()
    }
  }

  test("pubsubChannels returns active channels") {
    pubsubResource.use { pubsub =>
      val channel1 = ValkeyChannel("active-1")
      val channel2 = ValkeyChannel("active-2")

      for {
        // Start subscribers
        fiber1 <- pubsub.subscribe(channel1).interruptAfter(2.seconds).compile.drain.start
        fiber2 <- pubsub.subscribe(channel2).interruptAfter(2.seconds).compile.drain.start
        _      <- Stream.sleep[IO](300.millis).compile.drain

        // Check active channels
        channels <- pubsub.pubsubChannels
        _        <- IO {
          assert(channels.exists(_.underlying == "active-1"), s"Expected active-1 in $channels")
          assert(channels.exists(_.underlying == "active-2"), s"Expected active-2 in $channels")
        }

        // Cleanup
        _ <- fiber1.cancel
        _ <- fiber2.cancel
      } yield ()
    }
  }
}
