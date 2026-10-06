# arch-caching-patterns

Eight small Java programs that each show one caching pattern on a tiny Orders example, with the consistency risk of that pattern demonstrated and asserted.

## What is inside

| Folder | What it shows | Run |
| --- | --- | --- |
| [`cache-aside`](./cache-aside) | App-managed cache: read, fill on miss, invalidate on write; hit and miss counters; the stale-fill race | `java Main.java` |
| [`read-through`](./read-through) | A cache that loads by itself on a miss, with negative caching and its stale-data risks | `java Main.java` |
| [`write-through`](./write-through) | Writes go to the DB and then the cache; what happens when either one fails | `java Main.java` |
| [`write-behind`](./write-behind) | Writes land in the cache and are flushed later in coalesced batches; data loss on a crash | `java Main.java` |
| [`ttl-and-invalidation`](./ttl-and-invalidation) | Expiry by TTL versus an explicit invalidation event, using a fake clock | `java Main.java` |
| [`stampede-protection`](./stampede-protection) | 50 concurrent misses on one key: naive loading versus single-flight | `java Main.java` |
| [`multi-level-caffeine-redis`](./multi-level-caffeine-redis) | Per-node L1 in front of a shared L2 in front of the DB, with LRU bound and short L1 TTL | `java Main.java` |
| [`http-caching-etag`](./http-caching-etag) | `Cache-Control` and `ETag` / `If-None-Match` revalidation with a 304, on a local HTTP server | `java Main.java` |

Run each command from inside its folder.

## Prerequisites

- Java 21 (each program is a single-file source launch, no build tool).
- Docker is only needed if you want the optional Redis container in `cache-aside/compose.yaml`.

## How to read it

Start with `cache-aside`, then `read-through`, `write-through` and `write-behind`, which differ in who talks to the database. Redis and Caffeine are not used: the programs model them with plain JDK maps, so the stale-data cases can be asserted deterministically. No program has been run against a real Redis or a real Caffeine cache.
