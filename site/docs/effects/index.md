---
layout: docs
title:  "Effects API"
number: 5
position: 5
---

# Effects API

The API that operates at the effect level `F[_]` on top of `cats-effect`. All commands return `F[ValkeyResponse[A]]`.

- **[Strings API](strings.md)** -- GET, SET, INCR, APPEND, and other string operations
- **[Hashes API](hashes.md)** -- Field-value maps with HSET, HGET, HGETALL, and field expiration
- **[Keys API](keys.md)** -- Key management: DEL, EXISTS, EXPIRE, TTL, RENAME, SCAN
- **[Lists API](lists.md)** -- Ordered collections with push/pop and blocking operations
- **[Sets API](sets.md)** -- Unordered unique collections with set algebra operations
- **[Sorted Sets API](sortedsets.md)** -- Score-ordered sets for leaderboards and range queries
- **[Streams API](streams.md)** -- Append-only log with consumer groups and blocking reads
- **[Geo API](geo.md)** -- Geospatial indexes with radius and bounding-box search
- **[Bitmaps API](bitmaps.md)** -- Bit-level operations on string values
- **[HyperLogLog API](hyperloglog.md)** -- Probabilistic cardinality estimation
- **[Connection API](connection.md)** -- PING, ECHO, SELECT, and client identification
- **[Server API](server.md)** -- INFO, CONFIG, DBSIZE, FLUSHDB, and server management
- **[Scripting API](scripting.md)** -- Server-side functions with FCALL and script management
