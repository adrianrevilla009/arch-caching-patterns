# ttl-and-invalidation

A single-file `Main.java` with a `TtlCache` driven by a fake clock (`now`), so expiry is tested without sleeping.

## Goal
Show the two ways to limit stale data: a TTL that expires entries on its own, and an explicit invalidation when an "order updated" event arrives.

## Run it
```
java Main.java
```
Expected:
```
ok: TTL only: stale value served for up to the whole TTL
ok: after TTL the entry expires and a reload would see PAID
ok: event invalidation drops the entry immediately
hits=1 misses=2 expired=1 OK
```

## What it proves
- With a 60 000 ms TTL and the DB changed to `PAID`, a read at 59 999 ms still returns `NEW`.
- At 60 000 ms the entry is expired and removed (`expired=1`), so the next load sees fresh data.
- Calling `invalidate("o-1")` removes the entry at once, without waiting for the TTL.

## Trade-offs
- A long TTL means more hits but a longer stale window; a short TTL means the reverse.
- Invalidation depends on the event arriving. The final comment in `Main.java` notes that a lost event leaves the entry until the TTL, so use both.
- The example calls `invalidate` directly; no message broker is involved, so delivery delay and loss are not exercised.

## When not to use it
- For data that must be exact on every read; neither TTL nor events guarantee it.
- When invalidation events cannot be produced reliably, rely on short TTLs alone.

Time is injected through a `LongSupplier`, so real expiry timing was not measured.
