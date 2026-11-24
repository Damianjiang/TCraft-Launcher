package tcraft;

import java.util.concurrent.atomic.LongAdder;

public final class NetSpeed {

    private static final LongAdder BYTES = new LongAdder();
    private static volatile long lastSampleNanos = System.nanoTime();
    private static volatile long lastSampleBytes;
    private static volatile long speed;

    private NetSpeed() {
    }

    public static void add(long bytes) {
        if (bytes > 0) BYTES.add(bytes);
    }

    public static long current() {
        long now = System.nanoTime();
        long total = BYTES.sum();
        long elapsed = now - lastSampleNanos;
        if (elapsed < 350_000_000L) return speed;
        long delta = Math.max(0, total - lastSampleBytes);
        speed = (long) (delta * 1_000_000_000.0 / Math.max(1, elapsed));
        lastSampleNanos = now;
        lastSampleBytes = total;
        return speed;
    }

    public static void reset() {
        lastSampleBytes = BYTES.sum();
        lastSampleNanos = System.nanoTime();
        speed = 0;
    }
}
