# read-through

A single-file `Main.java` with a generic `ReadThroughCache` that owns a loader function and remembers misses as well as hits.

## Goal
Show a cache that callers use alone: on a miss the cache calls the loader itself. Show the two ways it can serve wrong data, out-of-band DB changes and cached "not found" results.

## Run it
```
java Main.java
```
Expected:
```
ok: loader runs once for a hot key
ok: negative result cached: 404 hit the DB once
ok: stale until evicted (cache cannot see out-of-band writes)
ok: after evict the loader fetches fresh data
ok: negative entry hides a row created after the miss
hits=4 misses=3 dbCalls=3 OK
```

## What it proves
- Two `get("o-1")` calls trigger one loader call; two `get("o-404")` calls also trigger one, because `Optional.empty()` is stored (negative caching).
- After `db.put("o-1", "PAID")` behind the cache's back, `get` still returns `NEW` until `evict("o-1")` is called.
- A row `o-404` created after the miss stays invisible, because the empty entry is never refreshed.

## Trade-offs
- Negative caching protects the DB from repeated lookups of missing keys but hides rows created later; real code needs a short TTL for those entries. This example has no TTL.
- `get` is `synchronized`, so concurrent loads of different keys are serialised.
- Loader errors and the cache's size are not handled here.

## When not to use it
- When you need to read through a store with no loader abstraction, or per-call fallback logic; cache-aside gives the caller control.
- When data changes outside your service and you have no invalidation signal.

Not run against Redis or Caffeine: the store is a `ConcurrentHashMap`.
