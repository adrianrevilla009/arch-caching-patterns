# write-through

A single-file `Main.java` with a fake `Db` and `Cache` that can each be switched off, and a `write` method that updates both in order.

## Goal
Show write-through: every write goes to the DB first and then the cache in the same call, so a read right after a write is a hit. Show what each failure leaves behind.

## Run it
```
java Main.java
```
Expected:
```
ok: read right after write is a hit
ok: DB failure leaves both layers unchanged
ok: cache failure after DB commit => cache serves stale NEW while DB has PAID
hits=3 misses=0 OK
```

## What it proves
- After `write("o-1", "NEW")` the cache returns the value with zero misses.
- If the DB is down, `write` throws before touching the cache, so DB and cache both still hold `NEW`.
- If the cache is down after the DB write succeeds, the DB holds `PAID` while the cache keeps serving `NEW`: the two diverge, and nothing in the code repairs it.

## Trade-offs
- Writes are as slow as the DB plus the cache, and there is no transaction across the two.
- Writing the DB first avoids caching data that was never saved, at the cost of the divergence shown above.
- Values that are written but never read still occupy the cache.

## When not to use it
- For write-heavy data that is rarely read; the cache fills with entries nobody asks for.
- When the cache and DB must never disagree and you cannot add a retry or invalidation on cache failure.

Not run against real stores: `Db` and `Cache` are `HashMap`s with a `down` flag.
