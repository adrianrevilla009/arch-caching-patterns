import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Read-through: callers only talk to the cache; the cache itself knows how to load on a miss. */
public class Main {
    static class ReadThroughCache<K, V> {
        private final Map<K, Optional<V>> store = new ConcurrentHashMap<>();
        private final Function<K, V> loader;
        long hits, misses;
        ReadThroughCache(Function<K, V> loader) { this.loader = loader; }

        synchronized V get(K key) {
            Optional<V> e = store.get(key);
            if (e != null) { hits++; return e.orElse(null); }
            misses++;
            V v = loader.apply(key);
            store.put(key, Optional.ofNullable(v));   // negative caching: remember "not found" too
            return v;
        }
        void evict(K key) { store.remove(key); }
    }

    static final Map<String, String> db = new HashMap<>(Map.of("o-1", "NEW"));
    static int dbCalls = 0;

    public static void main(String[] args) {
        ReadThroughCache<String, String> cache = new ReadThroughCache<>(id -> { dbCalls++; return db.get(id); });

        check("NEW".equals(cache.get("o-1")) && "NEW".equals(cache.get("o-1")), "loader runs once for a hot key");
        check(cache.get("o-404") == null && cache.get("o-404") == null && dbCalls == 2, "negative result cached: 404 hit the DB once");

        db.put("o-1", "PAID");   // Consistency risk: DB changed behind the cache's back, nobody told it.
        check("NEW".equals(cache.get("o-1")), "stale until evicted (cache cannot see out-of-band writes)");
        cache.evict("o-1");
        check("PAID".equals(cache.get("o-1")), "after evict the loader fetches fresh data");

        db.put("o-404", "LATE");  // risk of negative caching: a row created later stays invisible
        check(cache.get("o-404") == null, "negative entry hides a row created after the miss");
        System.out.printf("hits=%d misses=%d dbCalls=%d OK%n", cache.hits, cache.misses, dbCalls);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("ok: " + what);
    }
}
