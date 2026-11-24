package tcraft;

public final class Memory {

    private Memory() {
    }

    // 占用超过一半时回收一次 大批导入后调用
    public static void trim() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        if (used < runtime.maxMemory() / 2) return;
        System.gc();
    }
}
