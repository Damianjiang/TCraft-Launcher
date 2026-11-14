package tcraft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 下载、安装、启动任务中心。 */
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
        public String versionId;
        public final List<SubTask> subs = new CopyOnWriteArrayList<>();

        public Task(String title) {
            this.title = title;
        }

        public void cancel() {
            canceled = true;
            if (onCancel != null) onCancel.run();
        }
    }

    public static final Tasks INSTANCE = new Tasks();
    public final List<Task> list = new CopyOnWriteArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private Tasks() {
    }

    public void addListener(Runnable r) {
        if (!listeners.contains(r)) listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private void fire() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    public void add(Task task) {
        list.add(0, task);
        fire();
    }

    public void changed() {
        fire();
    }

    public boolean hasActive() {
        for (Task t : list) if (t.state == State.RUNNING) return true;
        return false;
    }

    public double overall() {
        double sum = 0;
        int n = 0;
        for (Task t : list) {
            sum += t.state == State.DONE ? 1 : t.progress;
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    public void clearFinished() {
        list.removeIf(t -> t.state == State.DONE);
        fire();
    }
}
