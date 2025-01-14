package dev.profunktor.valkey4cats

import dev.profunktor.valkey4cats.arguments.*
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.model.ValkeyResponse.Ok

class GeoCommandsSuite extends ValkeyTestSuite {

  private val rome = GeoPosition(12.4964, 41.9028)
  private val paris = GeoPosition(2.3522, 48.8566)
  private val london = GeoPosition(-0.1278, 51.5074)
  private val berlin = GeoPosition(13.4050, 52.5200)
  private val madrid = GeoPosition(-3.7038, 40.4168)

  // ==================== GEOADD ====================

  test("GEOADD should add members to a geo set") {
    valkeyClient.use { valkey =>
      for {
        result <- valkey.geoAdd(
          "geo-cities",
          Map("Rome" -> rome, "Paris" -> paris)
        )
        _ <- valkey.del("geo-cities")
      } yield assertEquals(result, Ok(2L))
    }
  }

  test("GEOADD should return 0 when adding existing members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd("geo-dup", Map("Rome" -> rome))
        result <- valkey.geoAdd("geo-dup", Map("Rome" -> rome))
        _ <- valkey.del("geo-dup")
      } yield assertEquals(result, Ok(0L))
    }
  }

  test("GEOADD with options NX should only add new members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd("geo-nx", Map("Rome" -> rome))
        result <- valkey.geoAdd(
          "geo-nx",
          Map("Rome" -> paris, "London" -> london),
          GeoAddOptions(condition = Some(GeoAddCondition.OnlyIfDoesNotExist))
        )
        pos <- valkey.geoPos("geo-nx", "Rome")
        _ <- valkey.del("geo-nx")
      } yield {
        assertEquals(result, Ok(1L))
        val Ok(positions) = pos: @unchecked
        val Some(romePos) = positions.head: @unchecked
        assert(
          math.abs(romePos.longitude - rome.longitude) < 0.001,
          "Rome position should not have been updated"
        )
      }
    }
  }

  test("GEOADD with changed flag should count updated elements") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd("geo-ch", Map("Rome" -> rome))
        result <- valkey.geoAdd(
          "geo-ch",
          Map("Rome" -> paris),
          GeoAddOptions(changed = true)
        )
        _ <- valkey.del("geo-ch")
      } yield assertEquals(result, Ok(1L))
    }
  }

  // ==================== GEODIST ====================

  test("GEODIST should return distance between two members in meters") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd(
          "geo-dist",
          Map("Rome" -> rome, "Paris" -> paris)
        )
        dist <- valkey.geoDist("geo-dist", "Rome", "Paris")
        _ <- valkey.del("geo-dist")
      } yield {
        val Ok(Some(d)) = dist: @unchecked
        assert(
          d > 1_000_000 && d < 1_200_000,
          s"Rome-Paris distance should be ~1100km, got $d meters"
        )
      }
    }
  }

  test("GEODIST with unit should return distance in specified unit") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd(
          "geo-dist-km",
          Map("Rome" -> rome, "Paris" -> paris)
        )
        dist <- valkey.geoDist(
          "geo-dist-km",
          "Rome",
          "Paris",
          GeoUnit.Kilometers
        )
        _ <- valkey.del("geo-dist-km")
      } yield {
        val Ok(Some(d)) = dist: @unchecked
        assert(
          d > 1000 && d < 1200,
          s"Rome-Paris distance should be ~1100km, got $d km"
        )
      }
    }
  }

  test("GEODIST with miles should return distance in miles") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd(
          "geo-dist-mi",
          Map("Rome" -> rome, "Paris" -> paris)
        )
        dist <- valkey.geoDist("geo-dist-mi", "Rome", "Paris", GeoUnit.Miles)
        _ <- valkey.del("geo-dist-mi")
      } yield {
        val Ok(Some(d)) = dist: @unchecked
        assert(
          d > 600 && d < 800,
          s"Rome-Paris distance should be ~690 miles, got $d miles"
        )
      }
    }
  }

  test("GEODIST should return None for non-existent member") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd("geo-dist-none", Map("Rome" -> rome))
        dist <- valkey.geoDist("geo-dist-none", "Rome", "NonExistent")
        _ <- valkey.del("geo-dist-none")
      } yield assertEquals(dist, Ok(None))
    }
  }

  // ==================== GEOHASH ====================

  test("GEOHASH should return geohash strings for members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd("geo-hash", Map("Rome" -> rome, "Paris" -> paris))
        hashes <- valkey.geoHash("geo-hash", "Rome", "Paris")
        _ <- valkey.del("geo-hash")
      } yield {
        val Ok(h) = hashes: @unchecked
        assertEquals(h.length, 2)
        assert(h(0).isDefined, "Rome hash should be present")
        assert(h(1).isDefined, "Paris hash should be present")
        assert(h(0).get.nonEmpty, "Rome hash should be non-empty")
      }
    }
  }

  test("GEOHASH should return None for non-existent members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd("geo-hash-none", Map("Rome" -> rome))
        hashes <- valkey.geoHash("geo-hash-none", "Rome", "NonExistent")
        _ <- valkey.del("geo-hash-none")
      } yield {
        val Ok(h) = hashes: @unchecked
        assert(h(0).isDefined)
        assert(h(1).isEmpty)
      }
    }
  }

  // ==================== GEOPOS ====================

  test("GEOPOS should return positions for existing members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd("geo-pos", Map("Rome" -> rome, "Paris" -> paris))
        positions <- valkey.geoPos("geo-pos", "Rome", "Paris")
        _ <- valkey.del("geo-pos")
      } yield {
        val Ok(p) = positions: @unchecked
        assertEquals(p.length, 2)
        val Some(romePos) = p(0): @unchecked
        val Some(parisPos) = p(1): @unchecked
        assert(math.abs(romePos.longitude - rome.longitude) < 0.001)
        assert(math.abs(romePos.latitude - rome.latitude) < 0.001)
        assert(math.abs(parisPos.longitude - paris.longitude) < 0.001)
      }
    }
  }

  test("GEOPOS should return None for non-existent members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd("geo-pos-none", Map("Rome" -> rome))
        positions <- valkey.geoPos("geo-pos-none", "Rome", "NonExistent")
        _ <- valkey.del("geo-pos-none")
      } yield {
        val Ok(p) = positions: @unchecked
        assert(p(0).isDefined)
        assert(p(1).isEmpty)
      }
    }
  }

  // ==================== GEOSEARCH ====================

  test("GEOSEARCH by radius from member should return nearby members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd(
          "geo-search",
          Map(
            "Rome" -> rome,
            "Paris" -> paris,
            "London" -> london,
            "Berlin" -> berlin,
            "Madrid" -> madrid
          )
        )
        results <- valkey.geoSearch(
          "geo-search",
          GeoSearchFrom.FromMember[String]("Paris"),
          GeoSearchBy.ByRadius(600, GeoUnit.Kilometers)
        )
        _ <- valkey.del("geo-search")
      } yield {
        val Ok(members) = results: @unchecked
        assert(members.contains("Paris"), "Should include origin member")
        assert(members.contains("London"), "London is ~340km from Paris")
        assert(!members.contains("Rome"), "Rome is ~1100km from Paris")
      }
    }
  }

  test("GEOSEARCH by radius from coordinates should return nearby members") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd(
          "geo-search-coord",
          Map("Rome" -> rome, "Paris" -> paris, "London" -> london)
        )
        results <- valkey.geoSearch(
          "geo-search-coord",
          GeoSearchFrom.FromCoord[String](paris),
          GeoSearchBy.ByRadius(500, GeoUnit.Kilometers)
        )
        _ <- valkey.del("geo-search-coord")
      } yield {
        val Ok(members) = results: @unchecked
        assert(members.contains("Paris"))
        assert(members.contains("London"))
      }
    }
  }

  test("GEOSEARCH by box should return members within bounding box") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd(
          "geo-search-box",
          Map(
            "Rome" -> rome,
            "Paris" -> paris,
            "London" -> london,
            "Berlin" -> berlin
          )
        )
        results <- valkey.geoSearch(
          "geo-search-box",
          GeoSearchFrom.FromMember[String]("Paris"),
          GeoSearchBy.ByBox(2000, 2000, GeoUnit.Kilometers)
        )
        _ <- valkey.del("geo-search-box")
      } yield {
        val Ok(members) = results: @unchecked
        assert(
          members.nonEmpty,
          "Should find members in a 2000x2000km box around Paris"
        )
      }
    }
  }

  test("GEOSEARCH with count limit should respect limit") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd(
          "geo-search-count",
          Map(
            "Rome" -> rome,
            "Paris" -> paris,
            "London" -> london,
            "Berlin" -> berlin,
            "Madrid" -> madrid
          )
        )
        results <- valkey.geoSearch(
          "geo-search-count",
          GeoSearchFrom.FromMember[String]("Paris"),
          GeoSearchBy.ByRadius(5000, GeoUnit.Kilometers),
          GeoSearchResultOptions(
            count = Some(2),
            sortOrder = Some(SortOrder.Asc)
          )
        )
        _ <- valkey.del("geo-search-count")
      } yield {
        val Ok(members) = results: @unchecked
        assertEquals(members.length, 2)
        assertEquals(members.head, "Paris")
      }
    }
  }

  test("GEOSEARCH with ASC sort should return closest first") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd(
          "geo-search-asc",
          Map("Rome" -> rome, "Paris" -> paris, "London" -> london)
        )
        results <- valkey.geoSearch(
          "geo-search-asc",
          GeoSearchFrom.FromMember[String]("Paris"),
          GeoSearchBy.ByRadius(5000, GeoUnit.Kilometers),
          GeoSearchResultOptions(sortOrder = Some(SortOrder.Asc))
        )
        _ <- valkey.del("geo-search-asc")
      } yield {
        val Ok(members) = results: @unchecked
        assertEquals(members.head, "Paris")
      }
    }
  }

  test("GEOSEARCH with DESC sort should return farthest first") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd(
          "geo-search-desc",
          Map("Rome" -> rome, "Paris" -> paris, "London" -> london)
        )
        results <- valkey.geoSearch(
          "geo-search-desc",
          GeoSearchFrom.FromMember[String]("Paris"),
          GeoSearchBy.ByRadius(5000, GeoUnit.Kilometers),
          GeoSearchResultOptions(sortOrder = Some(SortOrder.Desc))
        )
        _ <- valkey.del("geo-search-desc")
      } yield {
        val Ok(members) = results: @unchecked
        assertEquals(members.head, "Rome")
      }
    }
  }

  test("GEOSEARCH on empty key should return empty list") {
    valkeyClient.use { valkey =>
      for {
        results <- valkey.geoSearch(
          "geo-search-empty",
          GeoSearchFrom.FromCoord[String](paris),
          GeoSearchBy.ByRadius(1000, GeoUnit.Kilometers)
        )
      } yield {
        val Ok(members) = results: @unchecked
        assert(members.isEmpty)
      }
    }
  }

  // ==================== Complex workflows ====================

  test("GEO workflow: add, dist, pos, search") {
    valkeyClient.use { valkey =>
      for {
        added <- valkey.geoAdd(
          "geo-wf",
          Map(
            "Rome" -> rome,
            "Paris" -> paris,
            "London" -> london,
            "Berlin" -> berlin
          )
        )
        dist <- valkey.geoDist("geo-wf", "Rome", "Paris", GeoUnit.Kilometers)
        pos <- valkey.geoPos("geo-wf", "Berlin")
        nearby <- valkey.geoSearch(
          "geo-wf",
          GeoSearchFrom.FromMember[String]("Berlin"),
          GeoSearchBy.ByRadius(1000, GeoUnit.Kilometers)
        )
        _ <- valkey.del("geo-wf")
      } yield {
        assertEquals(added, Ok(4L))
        val Ok(Some(d)) = dist: @unchecked
        assert(d > 1000 && d < 1200)
        val Ok(List(Some(berlinPos))) = pos: @unchecked
        assert(math.abs(berlinPos.longitude - berlin.longitude) < 0.001)
        val Ok(nearbyMembers) = nearby: @unchecked
        assert(nearbyMembers.contains("Berlin"))
        assert(nearbyMembers.contains("Paris"))
      }
    }
  }

  // ==================== GEOSEARCHSTORE ====================

  test("GEOSEARCHSTORE should store search results in destination") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd(
          "gss-src",
          Map(
            "Rome" -> rome,
            "Paris" -> paris,
            "London" -> london,
            "Berlin" -> berlin
          )
        )
        count <- valkey.geoSearchStore(
          "gss-dest",
          "gss-src",
          GeoSearchFrom.FromCoord(GeoPosition(10.0, 48.0)),
          GeoSearchBy.ByRadius(1000, GeoUnit.Kilometers)
        )
        members <- valkey.zrange("gss-dest", 0, -1)
        _ <- valkey.del("gss-src", "gss-dest")
      } yield {
        val Ok(c) = count: @unchecked
        assert(c >= 2)
        val Ok(ms) = members: @unchecked
        assert(ms.contains("Berlin"))
      }
    }
  }

  test("GEOSEARCHSTORE with result options should limit results") {
    valkeyClient.use { valkey =>
      for {
        _ <- valkey.geoAdd(
          "gss-src2",
          Map(
            "Rome" -> rome,
            "Paris" -> paris,
            "London" -> london,
            "Berlin" -> berlin,
            "Madrid" -> madrid
          )
        )
        count <- valkey.geoSearchStore(
          "gss-dest2",
          "gss-src2",
          GeoSearchFrom.FromCoord(GeoPosition(10.0, 48.0)),
          GeoSearchBy.ByRadius(2000, GeoUnit.Kilometers),
          GeoSearchResultOptions(
            sortOrder = Some(SortOrder.Asc),
            count = Some(2)
          )
        )
        _ <- valkey.del("gss-src2", "gss-dest2")
      } yield assertEquals(count, Ok(2L))
    }
  }
}
