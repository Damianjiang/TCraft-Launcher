package tcraft;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class Music {

    private static final List<Path> FILES = new ArrayList<>();
    private static MediaPlayer player;
    private static int index;
    private static int errorStreak;
    private static boolean paused;
    private static boolean available;
    private static javafx.util.Duration totalTime;

    private Music() {
    }

    public static synchronized void init(String gameDir) {
        FILES.clear();
        Path dir = Paths.get(gameDir, "TCL", "Music");
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.filter(Files::isRegularFile).filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a") || n.endsWith(".aac");
                    }).sorted().forEach(FILES::add);
                }
            }
        } catch (Exception ignored) {
        }
        available = !FILES.isEmpty();
        // 列表变短时 index 归零防越界
        if (index >= FILES.size()) index = 0;
    }

    // MediaPlayer 只能 FX 线程建，调用方保证
    public static synchronized void autoStart() {
        if (!available() || !Data.get().getBool("UiMusicAuto", false)) return;
        stopPlayer();
        try {
            boolean random = Data.get().getBool("UiMusicRandom", false);
            int start = random ? (int) (Math.random() * FILES.size()) : 0;
            play(start);
        } catch (Exception ignored) {
            available = false;
        }
    }

    public static Path folder(String gameDir) {
        return Paths.get(gameDir, "TCL", "Music");
    }

    // 设置页「刷新背景音乐」入口
    public static synchronized void reload(String gameDir) {
        init(gameDir);
        autoStart();
    }

    // 设置页「清空背景音乐」入口
    public static synchronized void clear() {
        if (player != null) {
            try {
                player.stop();
                player.dispose();
            } catch (Throwable ignored) {
            }
            player = null;
        }
        FILES.clear();
        index = 0;
        errorStreak = 0;
        paused = true;
        available = false;
        totalTime = null;
    }

    public static boolean available() {
        return available && !FILES.isEmpty();
    }

    public static int trackCount() {
        return FILES.size();
    }

    public static java.util.List<Path> tracks(String gameDir) {
        java.util.List<Path> out = new ArrayList<>();
        Path dir = folder(gameDir);
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.filter(Files::isRegularFile).filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".mp3") || n.endsWith(".wav")
                                || n.endsWith(".m4a") || n.endsWith(".aac");
                    }).sorted().forEach(out::add);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean isPaused() {
        return paused || player == null;
    }

    private static synchronized void stopPlayer() {
        if (player != null) {
            try {
                player.stop();
                player.dispose();
            } catch (Throwable ignored) {
            }
            player = null;
        }
        totalTime = null;
    }

    public static String currentName() {
        if (!available()) return "";
        return FILES.get(index % FILES.size()).getFileName().toString();
    }

    public static synchronized void toggle() {
        if (!available()) return;
        if (player == null) {
            play(index);
            return;
        }
        if (paused) {
            player.play();
            paused = false;
        } else {
            player.pause();
            paused = true;
        }
    }

    public static synchronized void next() {
        if (!available()) return;
        int next = (index + 1) % FILES.size();
        if (Data.get().getBool("UiMusicRandom", false) && FILES.size() > 1) {
            next = (index + 1 + (int) (Math.random() * (FILES.size() - 1))) % FILES.size();
        }
        play(next);
        paused = false;
    }

    public static synchronized void pauseForGame() {
        if (player != null && !paused) {
            player.pause();
            paused = true;
        }
    }

    public static synchronized void resumeAfterGame() {
        if (!available() || player == null) return;
        player.play();
        paused = false;
    }

    public static int volumePercent() {
        int legacy = Data.get().getInt("UiMusicVolume", -1);
        if (legacy >= 0) return Math.max(0, Math.min(100, legacy / 10));
        return Math.max(0, Math.min(100, Data.get().getInt("UiVolume", 60)));
    }

    private static void play(int i) {
        if (player != null) {
            player.stop();
            player.dispose();
        }
        index = i;
        try {
            Media media = new Media(FILES.get(i).toUri().toString());
            player = new MediaPlayer(media);
            // 时长异步解析，构造后 getDuration 恒 UNKNOWN
            totalTime = null;
            player.totalDurationProperty().addListener((o, was, now) -> totalTime = now);
            boolean multiple = FILES.size() > 1;
            player.setCycleCount(multiple ? 1 : MediaPlayer.INDEFINITE);
            player.setVolume(volumePercent() / 100.0);
            player.play();
            paused = false;
            // 播 1.5s 才清零连败计数
            player.currentTimeProperty().addListener((o, was, now) -> {
                if (now != null && now.toMillis() > 1500) errorStreak = 0;
            });
            // 不挂 onError 连播会静默停住
            final MediaPlayer mp = player;
            player.setOnError(() -> javafx.application.Platform.runLater(() -> {
                // 已被切走的旧播放器的迟到回调不计数
                if (player != mp) return;
                errorStreak++;
                if (errorStreak >= FILES.size()) {
                    stopPlayer();
                    paused = true;
                    return;
                }
                Music.next();
            }));
            // 直接 dispose 有重入风险，挪下一帧
            if (multiple) player.setOnEndOfMedia(() -> javafx.application.Platform.runLater(Music::next));
        } catch (Exception e) {
            player = null;
            paused = true;
            // 构造期失败也走同一熔断
            errorStreak++;
            if (errorStreak < FILES.size()) {
                javafx.application.Platform.runLater(Music::next);
            }
        }
    }

    public static void setVolume(int percent) {
        if (player != null) player.setVolume(Math.max(0, Math.min(1, percent / 100.0)));
    }

    // 当前曲目播放进度 0..1，不可用时返回 -1
    public static double progress() {
        if (player == null || !available() || totalTime == null) return -1;
        try {
            javafx.util.Duration now = player.getCurrentTime();
            double total = totalTime.toMillis();
            if (now == null || total <= 0 || Double.isNaN(total)
                    || Double.isInfinite(total)) return -1;
            return Math.max(0, Math.min(1, now.toMillis() / total));
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
