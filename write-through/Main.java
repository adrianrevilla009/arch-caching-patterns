import java.util.*;

/** Write-through: every write goes to the DB and the cache in the same call, so reads never miss after a write. */
public class Main {
    static class Db {
        final Map<String, String> rows = new HashMap<>();
        boolean down;
        void put(String k, String v) { if (down) throw new IllegalStateException("db down"); rows.put(k, v); }
    }
    static class Cache {
        final Map<String, String> entries = new HashMap<>();
        boolean down; int hits, misses;
        void put(String k, String v) { if (down) throw new IllegalStateException("cache down"); entries.put(k, v); }
        String get(String k) { String v = entries.get(k); if (v == null) misses++; else hits++; return v; }
    }

    static final Db db = new Db();
    static final Cache cache = new Cache();

    /** DB first: if it fails nothing is cached. If the cache then fails the two diverge (documented risk). */
    static void write(String k, String v) { db.put(k, v); cache.put(k, v); }

    public static void main(String[] args) {
        write("o-1", "NEW");
        check("NEW".equals(cache.get("o-1")) && cache.misses == 0, "read right after write is a hit");

        db.down = true;
        try { write("o-1", "PAID"); } catch (IllegalStateException e) { /* expected */ }
        db.down = false;
        check("NEW".equals(cache.get("o-1")) && "NEW".equals(db.rows.get("o-1")), "DB failure leaves both layers unchanged");

        cache.down = true;
        try { write("o-1", "PAID"); } catch (IllegalStateException e) { /* expected */ }
        cache.down = false;
        check("PAID".equals(db.rows.get("o-1")) && "NEW".equals(cache.get("o-1")),
              "cache failure after DB commit => cache serves stale NEW while DB has PAID");
        System.out.printf("hits=%d misses=%d OK%n", cache.hits, cache.misses);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("ok: " + what);
    }
}
