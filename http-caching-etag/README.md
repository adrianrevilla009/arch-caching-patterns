# http-caching-etag

A single-file `Main.java` that starts the JDK `HttpServer` on a free local port, serves one order with `ETag` and `Cache-Control`, and calls it with `java.net.http.HttpClient`.

## Goal
Show HTTP-level caching: `Cache-Control` tells clients how long they may reuse a response, and `ETag` with `If-None-Match` lets them revalidate cheaply with a 304.

## Run it
```
java Main.java
```
Expected:
```
ok: first GET: 200 with ETag and Cache-Control
ok: revalidation with unchanged data: 304, no body
ok: changed data: new ETag, full 200
bodiesSent=2 notModified=1 OK
```

## What it proves
- The first `GET /orders/o-1` returns 200 with a quoted ETag (first 8 bytes of a SHA-256 of the body, hex) and `Cache-Control: max-age=30, must-revalidate`.
- Sending that tag in `If-None-Match` while the data is unchanged returns 304 with an empty body.
- After the order status changes to `PAID`, the same `If-None-Match` gets a full 200 and a different ETag.

## Trade-offs
- The server still builds the response to compute the ETag; revalidation saves bandwidth, not server work.
- The Java client used here does not cache responses itself, so the program sends the revalidation headers by hand. It does not show a browser reusing a response for 30 s.
- `max-age=30` means clients may skip the server entirely for 30 s and see old data.

## When not to use it
- For per-user or secret responses served through shared caches, unless you add `private` or `no-store`.
- For data that changes on every request, where the ETag never matches.

Tested only against the local server in the same process, not through a CDN or browser.
