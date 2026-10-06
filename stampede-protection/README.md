# stampede-protection

A single-file `Main.java` that fires 50 concurrent readers at one cold key through a naive loader and through a single-flight loader.

## Goal
Show the cache stampede: many concurrent misses each go to the database. Show that single-flight lets one thread load while the others wait for its result.

## Run it
```
java Main.java
```
Expected:
```
naive DB loads=50, single-flight DB loads=1
OK
```

## What it proves
- In `naive`, 50 virtual threads released together by a latch each see an empty cache and each call the 200 ms `slowDb` (50 loads in the run above).
- In `singleFlight`, the first thread registers a `CompletableFuture` in `inFlight` with `putIfAbsent`; the others call `join()` on it. The DB is loaded once.
- The program throws an `AssertionError` if the naive run does not show at least 2 loads or the protected run is not exactly 1.

## Trade-offs
- The naive count depends on thread timing; it is asserted only to be 2 or more, and was 50 here.
- Single-flight works inside one JVM. With several app instances each one still loads once.
- If the loader is slow or fails, all waiting callers wait or fail together; there is no timeout.

## When not to use it
- When keys are rarely hot or the load is cheap; the extra bookkeeping buys nothing.
- When you need protection across many instances; use a distributed lock or early refresh instead.

Not run against Redis: the cache is a `ConcurrentHashMap` and the database is a sleep.
