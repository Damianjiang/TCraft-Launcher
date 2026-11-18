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
        if (available) {
            try {
                boolean random = Data.get().getBool("UiMusicRandom", false);
                int start = random ? (int) (Math.random() * FILES.size()) : 0;
                play(start);
                if (!Data.get().getBool("UiMusicAuto", false)) {
                    if (player != null) player.pause();
                    paused = true;
                }
            } catch (Exception ignored) {
                available = false;
            }
        }
    }

    public static Path folder(String gameDir) {
        return Paths.get(gameDir, "TCL", "Music");
    }

    // 重新扫描音乐文件夹并从头播放，供设置页「刷新背景音乐」使用
    public static synchronized void reload(String gameDir) {
        init(gameDir);
    }

    // 停止播放并清空列表，供设置页「清空背景音乐」使用
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

    public static String currentName() {
        if (!available()) return "";
        return FILES.get(index).getFileName().toString();
    }

    public static synchronized void toggle() {
        if (!available()) return;
        if (player == null) play(index);
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
            totalTime = media.getDuration();
            player = new MediaPlayer(media);
            boolean multiple = FILES.size() > 1;
            player.setCycleCount(multiple ? 1 : MediaPlayer.INDEFINITE);
            player.setVolume(volumePercent() / 100.0);
            player.play();
            paused = false;
            if (multiple) player.setOnEndOfMedia(Music::next);
        } catch (Exception e) {
            player = null;
            paused = true;
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
