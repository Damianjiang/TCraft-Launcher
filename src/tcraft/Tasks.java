package tcraft;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Tasks {

    public enum State { RUNNING, DONE, FAILED, CANCELED }

    public static class SubTask {
        public final String name;
        public volatile State state = State.RUNNING;
        public volatile double progress;

        public SubTask(String name) {
            this.name = name;
        }
    }

    public static class Task {
        public final String title;
        public final long created = System.currentTimeMillis();
        public volatile State state = State.RUNNING;
        public volatile double progress;
        public volatile String stage = "准备中";
        public volatile String speed = "";
        public volatile int remainingFiles;
        public volatile int threads;
        public volatile boolean canceled;
        public volatile String error;
        public Runnable onCancel;
        public volatile String pendingText = "";
        public volatile Runnable pendingAction;
        public final List<SubTask> subs = new CopyOnWriteArrayList<>();

        public Task(String title) {
            this.title = title;
        }

        public void cancel() {
            if (canceled) return;
            canceled = true;
            stage = "正在取消…";
            if (onCancel != null) onCancel.run();
        }
    }

    public static final Tasks INSTANCE = new Tasks();
    public final List<Task> list = new CopyOnWriteArrayList<>();

    private Tasks() {
    }

    public void add(Task task) {
        list.add(0, task);
        trim();
    }

    // 失败/取消也算结束 最多 40 条
    private static final int MAX_FINISHED = 40;

    private void trim() {
        // add() may run on worker threads; snapshot first and remove by value, so two
        // concurrent trims cannot hit IndexOutOfBounds on a shifting list.
        List<Task> finished = new ArrayList<>();
        for (Task t : list) if (t.state != State.RUNNING) finished.add(t);
        int drop = finished.size() - MAX_FINISHED;
        if (drop <= 0) return;
        // List head holds the newest, so the tail is the oldest entry to drop first.
        int i = finished.size() - 1;
        while (i >= 0 && drop > 0) {
            if (list.remove(finished.get(i))) drop--;
            i--;
        }
    }

    public boolean hasActive() {
        return !active().isEmpty();
    }

    public List<Task> active() {
        List<Task> out = new ArrayList<>();
        for (Task t : list) if (t.state == State.RUNNING) out.add(t);
        return out;
    }

    public double overall() {
        double sum = 0;
        int active = 0;
        boolean done = false;
        boolean failed = false;
        for (Task t : list) {
            switch (t.state) {
                case RUNNING -> {
                    sum += Math.max(0, Math.min(1, t.progress));
                    active++;
                }
                case DONE -> done = true;
                case FAILED -> failed = true;
                default -> {
                }
            }
        }
        if (active > 0) return sum / active;
        if (done && !failed) return 1;
        // 空任务按 100 % 显示
        return list.isEmpty() ? 1 : 0;
    }

    public void clearFinished() {
        list.removeIf(t -> t.state != State.RUNNING);
    }
}
