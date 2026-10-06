import java.util.*;

/** Write-behind: writes land in the cache and are flushed to the DB later, in coalesced batches. */
public class Main {
    static final Map<String, String> db = new HashMap<>();
    static int dbWrites = 0;

    static class WriteBehindCache {
        final Map<String, String> cache = new HashMap<>();
        final Map<String, String> dirty = new LinkedHashMap<>();   // latest value per key = coalescing

        void put(String k, String v) { cache.put(k, v); dirty.put(k, v); }
        String get(String k) { return cache.get(k); }

        void flush() {                                              // a scheduler would call this every N ms
            for (var e : dirty.entrySet()) { db.put(e.getKey(), e.getValue()); dbWrites++; }
            dirty.clear();
        }
        /** Simulated process crash: cache memory is gone, the dirty set with it. */
        void crash() { cache.clear(); dirty.clear(); }
    }

    public static void main(String[] args) {
        WriteBehindCache c = new WriteBehindCache();
        for (int i = 1; i <= 100; i++) c.put("o-1", "qty=" + i);    // 100 updates of one hot order
        check(dbWrites == 0 && db.isEmpty(), "nothing in the DB yet (it lags the cache)");
        check("qty=100".equals(c.get("o-1")), "reads see the latest value from the cache");
        c.flush();
        check(dbWrites == 1 && "qty=100".equals(db.get("o-1")), "100 writes coalesced into 1 DB write");

        c.put("o-2", "NEW");
        c.crash();                                                   // before the next flush
        c.flush();
        check(!db.containsKey("o-2"), "crash before flush loses the acknowledged write o-2 (durability risk)");
        System.out.println("dbWrites=" + dbWrites + " OK");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("ok: " + what);
    }
}
