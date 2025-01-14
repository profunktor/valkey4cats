package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

class PubSubCommandsSuite extends ValkeyTestSuite {

  test("PUBLISH should succeed even with no subscribers") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.publish("test-channel", "hello")
      } yield assertEquals(result, Ok(()))
    }
  }

  test("PUBSUB CHANNELS should return empty list when no active channels") {
    valkeyClient.use { valkey =>
      for {
        channels <- valkey.pubsubChannels
      } yield {
        val Ok(chs) = channels: @unchecked
        assert(chs.isEmpty)
      }
    }
  }

  test("PUBSUB CHANNELS with pattern should return empty list when no match") {
    valkeyClient.use { valkey =>
      for {
        channels <- valkey.pubsubChannels("nonexistent-*")
      } yield {
        val Ok(chs) = channels: @unchecked
        assert(chs.isEmpty)
      }
    }
  }

  test("PUBSUB NUMPAT should return 0 when no pattern subscriptions") {
    valkeyClient.use { valkey =>
      for {
        count <- valkey.pubsubNumPat
      } yield assertEquals(count, Ok(0L))
    }
  }

  test(
    "PUBSUB NUMSUB should return 0 subscribers for non-subscribed channels"
  ) {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.pubsubNumSub("ch1", "ch2")
      } yield {
        val Ok(subs) = result: @unchecked
        assertEquals(subs("ch1"), 0L)
        assertEquals(subs("ch2"), 0L)
      }
    }
  }

  test("PUBSUB NUMSUB with empty args should return empty map") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.pubsubNumSub()
      } yield assertEquals(result, Ok(Map.empty[String, Long]))
    }
  }
}
