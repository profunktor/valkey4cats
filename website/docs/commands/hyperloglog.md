---
sidebar_position: 11
title: HyperLogLog
---

# HyperLogLog API

Purely functional interface for the [HyperLogLog API](https://valkey.io/commands/#hyperloglog).

HyperLogLog is a probabilistic data structure used to estimate the cardinality (number of unique elements) of a set. It trades perfect accuracy for constant memory usage — a single HyperLogLog uses only ~12KB regardless of the number of elements added.

## Usage

```scala
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // PFADD - add elements
    _ <- valkey.pfadd("visitors:2024-01", "user:1", "user:2", "user:3")

    // PFCOUNT - get approximate cardinality
    count <- valkey.pfcount("visitors:2024-01")
    _ <- IO.println(s"Unique visitors: ${count.toOption}") // Some(3)

    // PFCOUNT across multiple HLLs (union cardinality)
    _ <- valkey.pfadd("visitors:2024-02", "user:2", "user:4", "user:5")
    total <- valkey.pfcount("visitors:2024-01", "visitors:2024-02")
    _ <- IO.println(s"Total unique: ${total.toOption}") // Some(5)

    // PFMERGE - merge multiple HLLs
    _ <- valkey.pfmerge("visitors:q1", "visitors:2024-01", "visitors:2024-02")
  yield ()
}
```

## Use cases

- **Unique visitor counting**: Track unique page views without storing each visitor ID
- **Distinct event counting**: Count unique events in a stream
- **Cardinality estimation**: Approximate COUNT(DISTINCT) for large datasets

## Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| PFADD | `pfadd(key, elements*)` | `F[ValkeyResponse[Boolean]]` |
| PFCOUNT | `pfcount(keys*)` | `F[ValkeyResponse[Long]]` |
| PFMERGE | `pfmerge(destkey, sourcekeys*)` | `F[ValkeyResponse[Unit]]` |
