---
layout: docs
title:  "Geo"
number: 12
---

# Geo API

Purely functional interface for the [Geo API](https://valkey.io/commands/#geo).

### Geo Commands usage

Once you have acquired a connection you can start using it:

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}
import dev.profunktor.valkey4cats.arguments.{GeoPosition, GeoUnit, GeoSearchFrom, GeoSearchBy}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // GEOADD - add locations
    added <- valkey.geoAdd("locations", Map(
      "Buenos Aires"  -> GeoPosition(-58.3816, -34.6037),
      "Rio de Janeiro" -> GeoPosition(-43.1729, -22.9068),
      "Montevideo"    -> GeoPosition(-56.1645, -34.9011),
      "Tokyo"         -> GeoPosition(139.6917, 35.6895)
    ))
    _ <- IO.println(s"Added: ${added.toOption}") // Some(4)

    // GEODIST - distance between two members
    dist <- valkey.geoDist("locations", "Buenos Aires", "Tokyo", GeoUnit.Kilometers)
    _ <- IO.println(s"Buenos Aires to Tokyo: ${dist.toOption} km")

    // GEOPOS - get positions
    positions <- valkey.geoPos("locations", "Rio de Janeiro", "Tokyo")
    _ <- IO.println(s"Positions: ${positions.toOption}")

    // GEOHASH - get geohash strings
    hashes <- valkey.geoHash("locations", "Buenos Aires", "Montevideo")
    _ <- IO.println(s"Geohashes: ${hashes.toOption}")

    // GEOSEARCH - find members within radius
    nearby <- valkey.geoSearch(
      "locations",
      GeoSearchFrom.FromMember("Montevideo"),
      GeoSearchBy.ByRadius(1000.0, GeoUnit.Kilometers)
    )
    _ <- IO.println(s"Within 1000km of Montevideo: ${nearby.toOption}")

    // GEOSEARCH - find members within box
    inBox <- valkey.geoSearch(
      "locations",
      GeoSearchFrom.FromCoord(GeoPosition(-55.0, -33.0)),
      GeoSearchBy.ByBox(3000.0, 2000.0, GeoUnit.Kilometers)
    )
    _ <- IO.println(s"In box: ${inBox.toOption}")

    // GEOSEARCHSTORE - store search results
    stored <- valkey.geoSearchStore(
      "nearby_montevideo",
      "locations",
      GeoSearchFrom.FromMember("Montevideo"),
      GeoSearchBy.ByRadius(2000.0, GeoUnit.Kilometers)
    )
    _ <- IO.println(s"Stored: ${stored.toOption}")
  yield ()
}
```

### Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| GEOADD | `geoAdd(key, members)` | `F[ValkeyResponse[Long]]` |
| GEOADD (opts) | `geoAdd(key, members, options)` | `F[ValkeyResponse[Long]]` |
| GEODIST | `geoDist(key, member1, member2)` | `F[ValkeyResponse[Option[Double]]]` |
| GEODIST (unit) | `geoDist(key, m1, m2, unit)` | `F[ValkeyResponse[Option[Double]]]` |
| GEOHASH | `geoHash(key, members*)` | `F[ValkeyResponse[List[Option[String]]]]` |
| GEOPOS | `geoPos(key, members*)` | `F[ValkeyResponse[List[Option[GeoPosition]]]]` |
| GEOSEARCH | `geoSearch(key, from, by)` | `F[ValkeyResponse[List[V]]]` |
| GEOSEARCH (opts) | `geoSearch(key, from, by, opts)` | `F[ValkeyResponse[List[V]]]` |
| GEOSEARCHSTORE | `geoSearchStore(dest, src, from, by)` | `F[ValkeyResponse[Long]]` |
