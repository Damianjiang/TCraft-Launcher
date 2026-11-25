package tcraft;

import java.util.concurrent.atomic.LongAdder;

public final class NetSpeed {

    private static final LongAdder BYTES = new LongAdder();
    private static final int WINDOW = 30;
    private static final long SAMPLE_MS = 300;
    private static final long[] SAMPLES = new long[WINDOW];
    private static int sampleCount;
    private static int sampleIndex;
    private static volatile long lastSampleNanos = System.nanoTime();
    private static volatile long lastSampleBytes;
    private static volatile long speed;

    private NetSpeed() {
    }

    public static void add(long bytes) {
        if (bytes > 0) BYTES.add(bytes);
    }

    // 加权滑动平均 防瞬时速度跳字
    public static synchronized long current() {
        long now = System.nanoTime();
        long total = BYTES.sum();
        long elapsed = now - lastSampleNanos;
        if (elapsed < SAMPLE_MS * 1_000_000L) return speed;
        long delta = Math.max(0, total - lastSampleBytes);
        long instant = (long) (delta * 1_000_000_000.0 / Math.max(1, elapsed));
        SAMPLES[sampleIndex] = instant;
        sampleIndex = (sampleIndex + 1) % WINDOW;
        if (sampleCount < WINDOW) sampleCount++;
        long sum = 0;
        long weightSum = 0;
        for (int i = 0; i < sampleCount; i++) {
            long weight = sampleCount - i;
            int at = (sampleIndex - 1 - i + WINDOW) % WINDOW;
            sum += SAMPLES[at] * weight;
            weightSum += weight;
        }
        speed = weightSum > 0 ? sum / weightSum : 0;
        lastSampleNanos = now;
        lastSampleBytes = total;
        return speed;
    }

    public static synchronized void reset() {
        lastSampleBytes = BYTES.sum();
        lastSampleNanos = System.nanoTime();
        speed = 0;
        sampleCount = 0;
        sampleIndex = 0;
        java.util.Arrays.fill(SAMPLES, 0);
    }
}
