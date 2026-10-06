# multi-level-caffeine-redis

A single-file `Main.java` with two `Node`s, each having a small LRU L1 map, sharing one L2 map in front of a DB map.

## Goal
Show a two-level cache: a fast per-node L1 (the role Caffeine plays) in front of a shared L2 (the role Redis plays). Show how a write on one node leaves the other node stale for a while.

## Run it
```
java Main.java
```
Expected:
```
ok: DB read once; A hits L1, B is served by shared L2
ok: writer node sees the new value
ok: other node's L1 is stale until its short TTL runs out (consistency risk)
ok: after L1 TTL node B converges via L2
ok: L1 is bounded: LRU evicted down to maxSize=2
A: l1=1 l2=0 db=5 | B: l1=1 l2=2 db=0 OK
```

## What it proves
- Node A reads `o-1` from the DB once; its second read hits L1 and node B's first read is served by L2, so the DB sees one read.
- After A writes `PAID`, A returns `PAID` but B still returns `NEW` from its own L1. At fake time 5 000 ms B's L1 entry has expired and it reads `PAID` from L2.
- L1 is an access-ordered `LinkedHashMap` capped at 2 entries; reading `x`, `y`, `z` leaves 2 entries.

## Trade-offs
- `write` clears only the writing node's L1, so other nodes stay stale up to the L1 TTL (5 s here). Real setups add pub/sub invalidation.
- Two levels mean two TTLs and two eviction policies to tune.
- Hit counters are per node and kept in plain fields.

## When not to use it
- When one level is fast enough; a single Redis or a single Caffeine cache is simpler.
- When nodes need to see writes at once.

Not run with the real libraries: neither Caffeine nor Redis is a dependency; both are plain JDK maps and the clock is fake.
