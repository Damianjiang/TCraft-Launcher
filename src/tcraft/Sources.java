package tcraft;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class Sources {

    private Sources() {
    }

    private static final Map<String, double[]> SPEED = new ConcurrentHashMap<>();
    private static final Map<String, Future<Double>> INFLIGHT = new ConcurrentHashMap<>();
    private static final long TTL_MS = 10 * 60 * 1000L;
    private static final int PROBE_BYTES = 128 * 1024;
    private static final long PROBE_READ_CAP = 256 * 1024;
    // 探测用独立线程池
    private static final java.util.concurrent.ExecutorService PROBE_POOL =
            java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "TCL-SourceProbe");
                t.setDaemon(true);
                return t;
            });

    public static List<String> rank(List<String> urls) {
        if (urls == null || urls.size() < 2) return urls;
        // 每个域名取一个样本 URL
        Map<String, String> sample = new LinkedHashMap<>();
        for (String u : urls) {
            String h = host(u);
            if (h != null && !sample.containsKey(h)) sample.put(h, u);
        }
        if (sample.size() < 2) return urls;

        // 同一域名只测一次
        Map<String, Future<Double>> jobs = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : sample.entrySet()) {
            if (cached(e.getKey()) != null) continue;
            final String h = e.getKey();
            final String url = e.getValue();
            try {
                jobs.put(h, INFLIGHT.computeIfAbsent(h, k -> PROBE_POOL.submit(() -> {
                    try {
                        double kbps = probe(url);
                        // 失败也缓存
                        SPEED.put(h, new double[]{kbps, System.currentTimeMillis()});
                        return kbps;
                    } finally {
                        INFLIGHT.remove(h);
                    }
                })));
            } catch (Exception ignored) {
            }
        }
        long deadline = System.currentTimeMillis() + 2500;
        for (Map.Entry<String, Future<Double>> e : jobs.entrySet()) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) break;
            try {
                e.getValue().get(left, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // 慢源按 -1 排序
            }
        }

        List<String> out = new ArrayList<>(urls);
        // 先取快照 防 TimSort 异常
        java.util.Map<String, Double> speeds = new java.util.HashMap<>();
        for (String u : out) speeds.putIfAbsent(host(u), speedOf(u));
        out.sort((a, b) -> Double.compare(speeds.get(host(b)), speeds.get(host(a))));
        return out;
    }

    public static double speedOf(String url) {
        Double k = cached(host(url));
        return k == null ? -1 : k;
    }

    private static Double cached(String host) {
        if (host == null) return null;
        double[] v = SPEED.get(host);
        if (v == null) return null;
        if (System.currentTimeMillis() - (long) v[1] > TTL_MS) {
            SPEED.remove(host);
            return null;
        }
        return v[0];
    }

    private static double probe(String url) {
        long t = System.nanoTime();
        try (Http.Body body = Http.open(url, Duration.ofSeconds(4), 0, PROBE_BYTES - 1)) {
            // 对端无视 Range 读满即停
            byte[] data = Fetch.readAll(body.in, 3000, PROBE_READ_CAP);
            long ms = Math.max(1, (System.nanoTime() - t) / 1_000_000);
            if (data.length <= 0) return -1;
            return data.length / 1024.0 / (ms / 1000.0);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String host(String url) {
        try {
            String h = java.net.URI.create(url).getHost();
            return h == null ? null : h.toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }
}
