import java.util.*;

/** Two levels: a small per-node L1 (Caffeine's role) in front of a shared L2 (Redis's role) in front of the DB. */
public class Main {
    static long now = 0;                                           // fake clock, ms
    static final Map<String, String> db = new HashMap<>();
    static final Map<String, String> l2 = new HashMap<>();         // shared by all nodes
    static int dbReads;

    static class Node {
        record E(String v, long exp) {}
        final String name; final int maxSize; final long l1Ttl;
        final LinkedHashMap<String, E> l1;                          // access-ordered => LRU eviction
        int l1Hits, l2Hits, dbHits;

        Node(String name, int maxSize, long l1Ttl) {
            this.name = name; this.maxSize = maxSize; this.l1Ttl = l1Ttl;
            this.l1 = new LinkedHashMap<>(16, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry<String, E> e) { return size() > Node.this.maxSize; }
            };
        }
        String get(String k) {
            E e = l1.get(k);
            if (e != null && e.exp() > now) { l1Hits++; return e.v(); }
            String v = l2.get(k);
            if (v != null) l2Hits++;
            else { v = db.get(k); dbReads++; dbHits++; if (v != null) l2.put(k, v); }
            if (v != null) l1.put(k, new E(v, now + l1Ttl));
            return v;
        }
        void write(String k, String v) { db.put(k, v); l2.remove(k); l1.remove(k); }   // only THIS node's L1 is cleared
    }

    public static void main(String[] args) {
        db.put("o-1", "NEW");
        Node a = new Node("A", 2, 5_000), b = new Node("B", 2, 5_000);
        a.get("o-1"); a.get("o-1"); b.get("o-1");
        check(a.dbHits == 1 && a.l1Hits == 1 && b.l2Hits == 1 && dbReads == 1, "DB read once; A hits L1, B is served by shared L2");

        a.write("o-1", "PAID");
        check("PAID".equals(a.get("o-1")), "writer node sees the new value");
        check("NEW".equals(b.get("o-1")), "other node's L1 is stale until its short TTL runs out (consistency risk)");
        now = 5_000;
        check("PAID".equals(b.get("o-1")), "after L1 TTL node B converges via L2");

        for (String k : List.of("x", "y", "z")) db.put(k, k);
        for (String k : List.of("x", "y", "z")) a.get(k);
        check(a.l1.size() == 2, "L1 is bounded: LRU evicted down to maxSize=2");
        System.out.printf("A: l1=%d l2=%d db=%d | B: l1=%d l2=%d db=%d OK%n",
            a.l1Hits, a.l2Hits, a.dbHits, b.l1Hits, b.l2Hits, b.dbHits);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("ok: " + what);
    }
}
