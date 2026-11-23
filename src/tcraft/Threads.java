package tcraft;

final class Threads {

    private Threads() {
    }

    // 统一入口 命名 + 守护线程
    static Thread start(String name, Runnable body) {
        Thread t = new Thread(body, name);
        t.setDaemon(true);
        t.start();
        return t;
    }
}
