package tcraft;

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

    private Tasks() {
    }

    public void add(Task task) {
        list.add(0, task);
        trim();
    }

    public void changed() {
    }

    // 失败/取消也算结束 最多 40 条
    private static final int MAX_FINISHED = 40;

    private void trim() {
        int finished = 0;
        for (Task t : list) if (t.state != State.RUNNING) finished++;
        int drop = finished - MAX_FINISHED;
        if (drop <= 0) return;
        for (int i = list.size() - 1; i >= 0 && drop > 0; i--) {
            if (list.get(i).state != State.RUNNING) {
                list.remove(i);
                drop--;
            }
        }
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
        list.removeIf(t -> t.state != State.RUNNING);
    }
}
