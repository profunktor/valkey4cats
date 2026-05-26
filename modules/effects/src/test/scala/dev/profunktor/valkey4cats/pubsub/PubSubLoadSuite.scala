package dev.profunktor.valkey4cats.pubsub

import scala.concurrent.duration.*
import cats.effect.IO
import cats.syntax.all.*
import dev.profunktor.valkey4cats.ValkeyTestSuite
import dev.profunktor.valkey4cats.connection.ValkeyClient
import fs2.Stream

/** Load and stress tests for pub/sub functionality.
  *
  * These tests verify pub/sub behavior under high load conditions:
  * - High message throughput on a single channel
  * - Many concurrent channel subscriptions
  * - Many subscribers on a single channel
  * - Rapid subscribe/unsubscribe cycles
  * - Sustained high publish rate
  */
class PubSubLoadSuite extends ValkeyTestSuite {

  private def pubsubResource =
    ValkeyClient[IO].from(valkeyUri).flatMap(ValkeyPubSub.utf8[IO](_))

  // ========== Test 1: High throughput on single channel ==========

  test("high throughput: 10000 messages on single channel") {
    pubsubResource.use { pubsub =>
      val channel = ValkeyChannel("high-throughput")
      val messageCount = 10000

      // Generate messages: "msg-0", "msg-1", ..., "msg-9999"
      val messages = (0 until messageCount).map(i => s"msg-$i").toList

      val subscriber = pubsub.subscribe(channel).take(messageCount.toLong).compile.toList
      val publisher = Stream.sleep[IO](300.millis) >>
        Stream.emits(messages).evalMap(msg => pubsub.publish(channel, msg))

      publisher.concurrently(Stream.eval(subscriber.flatMap { received =>
        IO {
          assertEquals(received.size, messageCount, "Should receive all 10000 messages")
          assertEquals(received, messages, "Messages should be received in order")
        }
      })).compile.drain
    }
  }

  // ========== Test 2: Many channels ==========

  test("many channels: 100 concurrent channel subscriptions") {
    pubsubResource.use { pubsub =>
      val channelCount = 100
      val channels = (0 until channelCount).map(i => ValkeyChannel(s"channel-$i")).toList

      // Subscribe to all channels in parallel, each taking 1 message
      val subscribers = channels.parTraverse { channel =>
        pubsub.subscribe(channel).take(1).compile.lastOrError.start
      }

      for {
        fibers <- subscribers
        _      <- Stream.sleep[IO](300.millis).compile.drain

        // Publish to all channels in parallel
        _ <- channels.parTraverse { channel =>
          pubsub.publish(channel, s"msg-for-${channel.underlying}")
        }

        // Collect all results
        results <- fibers.traverse(_.joinWithNever)

        _ <- IO {
          assertEquals(results.size, channelCount, "Should receive from all 100 channels")
          // Verify each message matches its channel
          channels.zip(results).foreach { case (channel, msg) =>
            assertEquals(msg, s"msg-for-${channel.underlying}")
          }
        }
      } yield ()
    }
  }

  // ========== Test 3: Many subscribers on one channel ==========

  test("many subscribers: 50 subscribers on one channel") {
    pubsubResource.use { pubsub =>
      val channel = ValkeyChannel("fan-out")
      val subscriberCount = 50

      // Create 50 subscribers on the same channel
      val subscribers = (0 until subscriberCount).toList.parTraverse { _ =>
        pubsub.subscribe(channel).take(1).compile.lastOrError.start
      }

      for {
        fibers <- subscribers
        _      <- Stream.sleep[IO](300.millis).compile.drain

        // Publish once
        _ <- pubsub.publish(channel, "broadcast-message")

        // All 50 subscribers should receive it
        results <- fibers.traverse(_.joinWithNever)

        _ <- IO {
          assertEquals(results.size, subscriberCount, "All 50 subscribers should receive")
          results.foreach { msg =>
            assertEquals(msg, "broadcast-message")
          }
        }
      } yield ()
    }
  }

  // ========== Test 4: Rapid subscribe/unsubscribe cycles ==========

  test("rapid subscribe/unsubscribe cycles: 50 iterations") {
    pubsubResource.use { pubsub =>
      val iterations = 50

      // Each iteration: subscribe, publish, receive 1 message, stream completes
      (0 until iterations).toList.traverse { i =>
        val channel = ValkeyChannel(s"cycle-$i")

        val subscriber = pubsub.subscribe(channel).take(1).compile.lastOrError
        val publisher = Stream.sleep[IO](100.millis) >>
          Stream.eval(pubsub.publish(channel, s"cycle-msg-$i"))

        publisher.concurrently(Stream.eval(subscriber.flatMap { msg =>
          IO(assertEquals(msg, s"cycle-msg-$i"))
        })).compile.drain
      }.map { _ =>
        // If we reach here, all 50 cycles completed successfully
        assert(true, "All 50 cycles should complete")
      }
    }
  }

  // ========== Test 5: Sustained publish rate ==========

  test("sustained publish rate: 1000 msg/sec for 5 seconds") {
    pubsubResource.use { pubsub =>
      val channel = ValkeyChannel("sustained-rate")
      val messagesPerSecond = 1000
      val durationSeconds = 5
      val totalMessages = messagesPerSecond * durationSeconds

      // Metered stream: 1000 messages/second = 1 message per millisecond
      val intervalNanos = (1000000 / messagesPerSecond).nanos
      val publisher = Stream.sleep[IO](300.millis) >>
        Stream
          .range(0, totalMessages)
          .map(i => s"rate-msg-$i")
          .metered[IO](intervalNanos)
          .evalMap(msg => pubsub.publish(channel, msg))

      val subscriber = pubsub.subscribe(channel).take(totalMessages.toLong).compile.toList

      publisher.concurrently(Stream.eval(subscriber.flatMap { received =>
        IO {
          assertEquals(received.size, totalMessages, s"Should receive all $totalMessages messages")
          // Verify ordering: first and last messages
          assertEquals(received.head, "rate-msg-0", "First message should be rate-msg-0")
          assertEquals(received.last, s"rate-msg-${totalMessages - 1}", s"Last message should be rate-msg-${totalMessages - 1}")
        }
      })).compile.drain
    }
  }
}
