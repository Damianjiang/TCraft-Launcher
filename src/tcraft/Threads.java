package tcraft;

final class Threads {

    private Threads() {
    }

    // 建线程 命名 + 守护
    static Thread start(String name, Runnable body) {
        Thread t = new Thread(body, name);
        t.setDaemon(true);
        t.start();
        return t;
    }
}
