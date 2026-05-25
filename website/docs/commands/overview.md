---
sidebar_position: 1
title: Overview
---

# Commands Overview

The API operates at the effect level `F[_]` on top of `cats-effect`. All commands return `F[ValkeyResponse[A]]`.

| Category | Description |
|----------|-------------|
| [Strings](strings.md) | GET, SET, INCR, APPEND, and other string operations |
| [Hashes](hashes.md) | Field-value maps with HSET, HGET, HGETALL, and field expiration |
| [Keys](keys.md) | Key management: DEL, EXISTS, EXPIRE, TTL, RENAME, SCAN |
| [Lists](lists.md) | Ordered collections with push/pop and blocking operations |
| [Sets](sets.md) | Unordered unique collections with set algebra operations |
| [Sorted Sets](sorted-sets.md) | Score-ordered sets for leaderboards and range queries |
| [Streams](streams.md) | Append-only log with consumer groups and blocking reads |
| [Geo](geo.md) | Geospatial indexes with radius and bounding-box search |
| [Bitmaps](bitmaps.md) | Bit-level operations on string values |
| [HyperLogLog](hyperloglog.md) | Probabilistic cardinality estimation |
| [Connection](connection.md) | PING, ECHO, SELECT, and client identification |
| [Server](server.md) | INFO, CONFIG, DBSIZE, FLUSHDB, and server management |
| [Scripting](scripting.md) | Server-side functions with FCALL and script management |
