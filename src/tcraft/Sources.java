package tcraft;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 下载源测速与排序，按速度择优。 */
public final class Sources {

    private Sources() {
    }

    /** host 到速度和采样时间。 */
    private static final Map<String, double[]> SPEED = new ConcurrentHashMap<>();
    /** 正在测速的 host，同一域名只发一次探测。 */
    private static final Map<String, Future<Double>> INFLIGHT = new ConcurrentHashMap<>();
    private static final long TTL_MS = 10 * 60 * 1000L;
    private static final int PROBE_BYTES = 128 * 1024;

    /** 按速度排序；首次最多等 2.5 秒测速，测不完的先按调用方顺序走。 */
    public static List<String> rank(List<String> urls) {
        if (urls == null || urls.size() < 2) return urls;
        // 每个域名取一个样本 URL
        Map<String, String> sample = new LinkedHashMap<>();
        for (String u : urls) {
            String h = host(u);
            if (h != null && !sample.containsKey(h)) sample.put(h, u);
        }
        if (sample.size() < 2) return urls;

        // 并发测速，同一域名只发一次；结果写缓存，供这次和下次排序
        Map<String, Future<Double>> jobs = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : sample.entrySet()) {
            if (cached(e.getKey()) != null) continue;
            final String h = e.getKey();
            final String url = e.getValue();
            try {
                jobs.put(h, INFLIGHT.computeIfAbsent(h, k -> Http.pool().submit(() -> {
                    try {
                        double kbps = probe(url);
                        // 失败也记下来，避免每个文件都重新探测一遍
                        SPEED.put(h, new double[]{kbps, System.currentTimeMillis()});
                        return kbps;
                    } finally {
                        INFLIGHT.remove(h);
                    }
                })));
            } catch (Exception ignored) {
            }
        }
        // 别让测速拖住下载：最多等 2.5 秒，没测完的当作未知
        long deadline = System.currentTimeMillis() + 2500;
        for (Map.Entry<String, Future<Double>> e : jobs.entrySet()) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) break;
            try {
                e.getValue().get(left, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // 慢源/超时，交给排序时的 -1 处理
            }
        }

        List<String> out = new ArrayList<>(urls);
        out.sort((a, b) -> Double.compare(speedOf(b), speedOf(a)));
        return out;
    }

    /** 清空测速结果，重新测。 */
    public static void invalidate() {
        SPEED.clear();
    }

    /** URL 速度，没测过返回 -1。 */
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

    /** 抓 128KB 样本测速。 */
    private static double probe(String url) {
        long t = System.nanoTime();
        try (Http.Body body = Http.open(url, Duration.ofSeconds(4), 0, PROBE_BYTES - 1)) {
            // 卡住不动就别一直占着线程，3 秒没数据直接算失败
            byte[] data = Fetch.readAll(body.in, 3000);
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
            return h == null ? null : h.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }
}
