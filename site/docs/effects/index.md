---
layout: docs
title:  "Effects API"
number: 5
position: 5
---

# Effects API

The API that operates at the effect level `F[_]` on top of `cats-effect`. All commands return `F[ValkeyResponse[A]]`.

- **[Strings API](strings.html)** -- GET, SET, INCR, APPEND, and other string operations
- **[Hashes API](hashes.html)** -- Field-value maps with HSET, HGET, HGETALL, and field expiration
- **[Keys API](keys.html)** -- Key management: DEL, EXISTS, EXPIRE, TTL, RENAME, SCAN
- **[Lists API](lists.html)** -- Ordered collections with push/pop and blocking operations
- **[Sets API](sets.html)** -- Unordered unique collections with set algebra operations
- **[Sorted Sets API](sortedsets.html)** -- Score-ordered sets for leaderboards and range queries
- **[Streams API](streams.html)** -- Append-only log with consumer groups and blocking reads
- **[Geo API](geo.html)** -- Geospatial indexes with radius and bounding-box search
- **[Bitmaps API](bitmaps.html)** -- Bit-level operations on string values
- **[HyperLogLog API](hyperloglog.html)** -- Probabilistic cardinality estimation
- **[Connection API](connection.html)** -- PING, ECHO, SELECT, and client identification
- **[Server API](server.html)** -- INFO, CONFIG, DBSIZE, FLUSHDB, and server management
- **[Scripting API](scripting.html)** -- Server-side functions with FCALL and script management
