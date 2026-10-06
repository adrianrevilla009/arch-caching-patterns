import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

/** HTTP caching: Cache-Control lets clients reuse a response; ETag + If-None-Match revalidates it with a 304. */
public class Main {
    static volatile String order = "{\"id\":\"o-1\",\"status\":\"NEW\"}";
    static final AtomicInteger bodiesSent = new AtomicInteger(), notModified = new AtomicInteger();

    static String etag(String body) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
        return "\"" + HexFormat.of().formatHex(h, 0, 8) + "\"";
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/orders/o-1", ex -> {
            try {
                String tag = etag(order);
                ex.getResponseHeaders().set("ETag", tag);
                ex.getResponseHeaders().set("Cache-Control", "max-age=30, must-revalidate");
                if (tag.equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {
                    notModified.incrementAndGet(); ex.sendResponseHeaders(304, -1);
                } else {
                    byte[] b = order.getBytes(StandardCharsets.UTF_8);
                    bodiesSent.incrementAndGet(); ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b);
                }
            } catch (Exception e) { ex.sendResponseHeaders(500, -1); }
            ex.close();
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/orders/o-1");
        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpResponse<String> r1 = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
            String tag = r1.headers().firstValue("ETag").orElseThrow();
            check(r1.statusCode() == 200 && r1.headers().firstValue("Cache-Control").orElse("").contains("max-age=30"), "first GET: 200 with ETag and Cache-Control");

            HttpResponse<String> r2 = client.send(HttpRequest.newBuilder(uri).header("If-None-Match", tag).build(), HttpResponse.BodyHandlers.ofString());
            check(r2.statusCode() == 304 && r2.body().isEmpty(), "revalidation with unchanged data: 304, no body");

            order = "{\"id\":\"o-1\",\"status\":\"PAID\"}";
            HttpResponse<String> r3 = client.send(HttpRequest.newBuilder(uri).header("If-None-Match", tag).build(), HttpResponse.BodyHandlers.ofString());
            check(r3.statusCode() == 200 && r3.body().contains("PAID") && !tag.equals(r3.headers().firstValue("ETag").get()), "changed data: new ETag, full 200");
            System.out.printf("bodiesSent=%d notModified=%d OK%n", bodiesSent.get(), notModified.get());
        } finally { server.stop(0); }
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("ok: " + what);
    }
}
