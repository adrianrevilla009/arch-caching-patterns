# cache-aside

A single-file `Main.java` implementing cache-aside over two maps (a "DB" and a Redis-like cache), plus a pinned Redis image in `compose.yaml`.

## Goal
Show the application-owned cache pattern: read from the cache, load from the DB on a miss and fill, and delete the cache entry on write. Also show the race that leaves a stale entry behind.

## Run it
```
java Main.java
```
Expected:
```
ok: read after write sees the new value
ok: metrics: hits=2 misses=2 dbReads=2
ok: stale entry survives (race window)
hit ratio=0.60 OK
```

## What it proves
- Three reads of `o-1` give 1 miss and 2 hits; after `write` deletes the entry, the next read misses and returns `PAID` (hits=2, misses=2, dbReads=2).
- With the reader and writer interleaved by hand in `main`, a slow reader puts the old `PAID` value into the cache after the writer stored `SHIPPED`. The cache then returns `PAID` while the DB holds `SHIPPED`.
- Hit and miss counters are plain `AtomicInteger`s; the final hit ratio printed is 0.60.

## Trade-offs
- Delete-after-write narrows the stale window but does not close it; only a TTL or versioned values bound the damage.
- Every caller must repeat the read and fill logic, so it is easy to skip invalidation in one code path.
- The first read after an invalidation always pays the DB cost.

## When not to use it
- When many callers need one shared loading policy; a read-through cache keeps that logic in one place.
- When stale reads are never acceptable.

Not run end to end against Redis: the cache here is a `ConcurrentHashMap`. `compose.yaml` starts `redis:7.4.1-alpine` on port 6379, but nothing in `Main.java` connects to it, and the container was not started.
