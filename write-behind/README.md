# write-behind

A single-file `Main.java` with a `WriteBehindCache` that keeps a dirty map, flushes it to the DB on demand, and can simulate a crash.

## Goal
Show write-behind: writes are acknowledged from the cache and reach the DB later, with repeated updates to one key merged into one DB write. Show the durability cost.

## Run it
```
java Main.java
```
Expected:
```
ok: nothing in the DB yet (it lags the cache)
ok: reads see the latest value from the cache
ok: 100 writes coalesced into 1 DB write
ok: crash before flush loses the acknowledged write o-2 (durability risk)
dbWrites=1 OK
```

## What it proves
- 100 `put` calls on `o-1` leave the DB empty until `flush()`, while `get` already returns `qty=100`.
- After `flush()` the DB has received exactly one write (`dbWrites=1`), because the dirty map keeps only the latest value per key.
- A `put` of `o-2` followed by `crash()` and `flush()` never reaches the DB: an acknowledged write is lost.

## Trade-offs
- Big reduction in DB writes for hot keys, paid for with a window where the DB is behind the cache.
- Cache memory is the only copy of unflushed data. A real implementation would need a durable queue.
- `flush()` is called by hand; there is no scheduler, retry, or ordering across keys, and the code is not thread-safe.

## When not to use it
- For orders, payments or anything that must not be lost on a crash.
- When other systems read the DB directly and need current data.

Not run against real stores: the cache and DB are `HashMap`s and the crash is simulated by clearing them.
