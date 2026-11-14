package tcraft;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** 背景音乐：扫 游戏目录/TCL/Music，支持播放 / 暂停 / 下一首。 */
public final class Music {

    private static final List<Path> FILES = new ArrayList<>();
    private static MediaPlayer player;
    private static int index;
    private static boolean paused;
    private static boolean available;

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
                play(0);
                if (player != null) player.pause();
                paused = true;
            } catch (Exception ignored) {
                available = false;
            }
        }
    }

    public static boolean available() {
        return available && !FILES.isEmpty();
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
        play((index + 1) % FILES.size());
        paused = false;
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
            // 单片无限循环，多片每首播一次再切下一首。
            boolean multiple = FILES.size() > 1;
            player.setCycleCount(multiple ? 1 : MediaPlayer.INDEFINITE);
            player.setVolume(Data.get().getInt("UiVolume", 60) / 100.0);
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
}
