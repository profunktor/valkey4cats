---
layout: docs
title:  "HyperLogLog"
number: 17
---

# HyperLogLog API

Purely functional interface for the [HyperLogLog API](https://valkey.io/commands/#hyperloglog).

HyperLogLog is a probabilistic data structure used to estimate the cardinality (number of unique elements) of a set. It trades perfect accuracy for constant memory usage -- a single HyperLogLog uses only ~12KB regardless of the number of elements added.

### HyperLogLog Commands usage

Once you have acquired a connection you can start using it:

```scala mdoc:compile-only
import cats.effect.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyResponse.{Ok, Err}

given Log[IO] = Log.Stdout.instance[IO]

Valkey[IO].utf8("valkey://localhost:6379").use { valkey =>
  for
    // PFADD - add elements to the HyperLogLog
    changed <- valkey.pfadd("visitors:2024-01", "user:1", "user:2", "user:3")
    _ <- IO.println(s"HLL altered: ${changed.toOption}") // Some(true)

    // Adding duplicates doesn't change the estimate
    notChanged <- valkey.pfadd("visitors:2024-01", "user:1", "user:2")
    _ <- IO.println(s"HLL altered: ${notChanged.toOption}") // Some(false)

    // PFCOUNT - get the approximate cardinality
    count <- valkey.pfcount("visitors:2024-01")
    _ <- IO.println(s"Unique visitors: ${count.toOption}") // Some(3)

    // PFCOUNT across multiple HLLs (union cardinality)
    _ <- valkey.pfadd("visitors:2024-02", "user:2", "user:4", "user:5")
    totalUnique <- valkey.pfcount("visitors:2024-01", "visitors:2024-02")
    _ <- IO.println(s"Total unique across months: ${totalUnique.toOption}") // Some(5)

    // PFMERGE - merge multiple HLLs into one
    _ <- valkey.pfmerge("visitors:q1", "visitors:2024-01", "visitors:2024-02")
    merged <- valkey.pfcount("visitors:q1")
    _ <- IO.println(s"Q1 unique visitors: ${merged.toOption}") // Some(5)
  yield ()
}
```

### Use cases

- **Unique visitor counting**: Track unique page views without storing each visitor ID
- **Distinct event counting**: Count unique events in a stream
- **Cardinality estimation**: Approximate COUNT(DISTINCT) for large datasets

### Available commands

| Command | Method | Return type |
|---------|--------|-------------|
| PFADD | `pfadd(key, elements*)` | `F[ValkeyResponse[Boolean]]` |
| PFCOUNT | `pfcount(keys*)` | `F[ValkeyResponse[Long]]` |
| PFMERGE | `pfmerge(destkey, sourcekeys*)` | `F[ValkeyResponse[Unit]]` |
