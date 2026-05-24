---
sidebar_position: 9
title: Geo
---

# Geo API

Purely functional interface for the [Geo API](https://valkey.io/commands/#geo).

## Usage

```scala
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
      "Buenos Aires"   -> GeoPosition(-58.3816, -34.6037),
      "Rio de Janeiro" -> GeoPosition(-43.1729, -22.9068),
      "Tokyo"          -> GeoPosition(139.6917, 35.6895)
    ))
    _ <- IO.println(s"Added: ${added.toOption}")

    // GEODIST - distance between two members
    dist <- valkey.geoDist("locations", "Buenos Aires", "Tokyo", GeoUnit.Kilometers)
    _ <- IO.println(s"Buenos Aires to Tokyo: ${dist.toOption} km")

    // GEOSEARCH - find members within radius
    nearby <- valkey.geoSearch(
      "locations",
      GeoSearchFrom.FromMember("Buenos Aires"),
      GeoSearchBy.ByRadius(2000.0, GeoUnit.Kilometers)
    )
    _ <- IO.println(s"Within 2000km: ${nearby.toOption}")

    // GEOSEARCH - find members within box
    inBox <- valkey.geoSearch(
      "locations",
      GeoSearchFrom.FromCoord(GeoPosition(-55.0, -33.0)),
      GeoSearchBy.ByBox(3000.0, 2000.0, GeoUnit.Kilometers)
    )
    _ <- IO.println(s"In box: ${inBox.toOption}")
  yield ()
}
```

## Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| GEOADD | `geoAdd(key, members)` | `F[ValkeyResponse[Long]]` |
| GEODIST | `geoDist(key, member1, member2)` | `F[ValkeyResponse[Option[Double]]]` |
| GEODIST (unit) | `geoDist(key, m1, m2, unit)` | `F[ValkeyResponse[Option[Double]]]` |
| GEOHASH | `geoHash(key, members*)` | `F[ValkeyResponse[List[Option[String]]]]` |
| GEOPOS | `geoPos(key, members*)` | `F[ValkeyResponse[List[Option[GeoPosition]]]]` |
| GEOSEARCH | `geoSearch(key, from, by)` | `F[ValkeyResponse[List[V]]]` |
| GEOSEARCHSTORE | `geoSearchStore(dest, src, from, by)` | `F[ValkeyResponse[Long]]` |
