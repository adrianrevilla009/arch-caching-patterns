import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Cache-aside: the application owns the cache. Read: cache, else DB then fill. Write: DB then invalidate. */
public class Main {
    static final Map<String, String> db = new ConcurrentHashMap<>();      // source of truth (Orders table)
    static final Map<String, String> cache = new ConcurrentHashMap<>();   // stand-in for Redis GET/SET/DEL
    static final AtomicInteger hits = new AtomicInteger(), misses = new AtomicInteger(), dbReads = new AtomicInteger();

    static String dbRead(String id) { dbReads.incrementAndGet(); return db.get(id); }

    static String read(String id) {
        String v = cache.get(id);
        if (v != null) { hits.incrementAndGet(); return v; }
        misses.incrementAndGet();
        v = dbRead(id);
        if (v != null) cache.put(id, v);
        return v;
    }

    static void write(String id, String v) { db.put(id, v); cache.remove(id); }

    public static void main(String[] args) {
        db.put("o-1", "NEW");
        read("o-1"); read("o-1"); read("o-1");                 // 1 miss, 2 hits
        write("o-1", "PAID");                                   // invalidates the entry
        check(read("o-1").equals("PAID"), "read after write sees the new value");
        check(hits.get() == 2 && misses.get() == 2 && dbReads.get() == 2, "metrics: hits=2 misses=2 dbReads=2");

        // Consistency risk, interleaved by hand: a slow reader fills the cache AFTER a writer invalidated it.
        cache.clear();
        String stale = dbRead("o-1");                           // reader: miss, reads PAID from the DB
        write("o-1", "SHIPPED");                                // writer: DB updated, cache entry deleted
        cache.put("o-1", stale);                                // reader finally fills the cache with old data
        check(read("o-1").equals("PAID") && db.get("o-1").equals("SHIPPED"), "stale entry survives (race window)");
        System.out.printf("hit ratio=%.2f OK%n", hits.get() / (double) (hits.get() + misses.get()));
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("ok: " + what);
    }
}
