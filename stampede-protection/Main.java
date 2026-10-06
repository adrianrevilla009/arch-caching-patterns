import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Stampede: N concurrent misses on one hot key. Naive code hits the DB N times; single-flight hits it once. */
public class Main {
    static final int THREADS = 50;
    static final AtomicInteger dbLoads = new AtomicInteger();

    static String slowDb(String id) {
        dbLoads.incrementAndGet();
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return "order:" + id;
    }

    static final Map<String, String> cache = new ConcurrentHashMap<>();
    static final Map<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();

    static String naive(String id) {
        String v = cache.get(id);
        if (v == null) { v = slowDb(id); cache.put(id, v); }
        return v;
    }

    static String singleFlight(String id) {
        String v = cache.get(id);
        if (v != null) return v;
        CompletableFuture<String> mine = new CompletableFuture<>();
        CompletableFuture<String> f = inFlight.putIfAbsent(id, mine);
        if (f != null) return f.join();                    // someone else is loading: wait for their result
        try {
            v = slowDb(id); cache.put(id, v); mine.complete(v); return v;
        } catch (RuntimeException e) { mine.completeExceptionally(e); throw e; }
        finally { inFlight.remove(id); }
    }

    static int storm(java.util.function.Function<String, String> reader) throws Exception {
        cache.clear(); dbLoads.set(0);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> fs = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) fs.add(pool.submit(() -> { go.await(); return reader.apply("o-1"); }));
            go.countDown();
            for (Future<String> f : fs) f.get();
        }
        return dbLoads.get();
    }

    public static void main(String[] args) throws Exception {
        int naive = storm(Main::naive);
        int protectedLoads = storm(Main::singleFlight);
        System.out.printf("naive DB loads=%d, single-flight DB loads=%d%n", naive, protectedLoads);
        if (naive < 2) throw new AssertionError("expected a stampede without protection");
        if (protectedLoads != 1) throw new AssertionError("expected exactly one load with single-flight");
        System.out.println("OK");
    }
}
