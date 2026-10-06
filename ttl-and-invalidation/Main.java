import java.util.*;
import java.util.function.LongSupplier;

/** TTL bounds staleness passively; an invalidation event removes it actively. Time is injected, so no sleeping. */
public class Main {
    static long now = 0;                                   // fake clock in ms
    static final LongSupplier clock = () -> now;

    record Entry(String value, long expiresAt) {}

    static class TtlCache {
        final Map<String, Entry> map = new HashMap<>();
        final long ttl; int hits, misses, expired;
        TtlCache(long ttl) { this.ttl = ttl; }

        String get(String k) {
            Entry e = map.get(k);
            if (e != null && e.expiresAt() > clock.getAsLong()) { hits++; return e.value(); }
            if (e != null) { expired++; map.remove(k); }
            misses++;
            return null;
        }
        void put(String k, String v) { map.put(k, new Entry(v, clock.getAsLong() + ttl)); }
        void invalidate(String k) { map.remove(k); }       // called by an "OrderUpdated" event handler
    }

    public static void main(String[] args) {
        Map<String, String> db = new HashMap<>(Map.of("o-1", "NEW"));
        TtlCache cache = new TtlCache(60_000);
        cache.put("o-1", db.get("o-1"));

        db.put("o-1", "PAID");                             // DB changes, no event yet
        now = 59_999;
        check("NEW".equals(cache.get("o-1")), "TTL only: stale value served for up to the whole TTL");
        now = 60_000;
        check(cache.get("o-1") == null && cache.expired == 1, "after TTL the entry expires and a reload would see PAID");

        cache.put("o-1", db.get("o-1"));
        db.put("o-1", "SHIPPED");
        cache.invalidate("o-1");                           // event-driven: staleness window ~ event latency
        check(cache.get("o-1") == null, "event invalidation drops the entry immediately");
        System.out.printf("hits=%d misses=%d expired=%d OK%n", cache.hits, cache.misses, cache.expired);
        // Lost event => entry lives until TTL: always use both (TTL is the safety net for missed events).
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("ok: " + what);
    }
}
