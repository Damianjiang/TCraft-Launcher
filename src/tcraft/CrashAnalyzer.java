package tcraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CrashAnalyzer {

    public record Hit(String cause, String advice, String evidence) {
    }

    public record Result(List<Hit> hits, List<String> fallback) {
        public boolean found() {
            return !hits.isEmpty();
        }
    }

    private static final String[][] RULES = {
            {"OutOfMemoryError", "内存不足", "把最大内存调高（设置 › 启动 › 内存），并检查是否安装了过多大型 Mod。"},
            {"Java heap space", "内存不足", "把最大内存调高，或减少同时加载的 Mod / 光影。"},
            {"UnsupportedClassVersionError", "Java 版本不匹配", "这个版本需要更高版本的 Java；到 设置 › Java 重新选择运行时。"},
            {"requires Java", "Java 版本不匹配", "按崩溃提示的版本要求更换 Java。"},
            {"Missing or unsupported mandatory dependencies", "缺少前置 Mod", "按日志列出的 modid 补齐前置 Mod，或换用兼容版本。"},
            {"Mod resolution failed", "Mod 依赖解析失败", "检查缺失依赖、重复 Mod 与版本冲突。"},
            {"Duplicate mods", "Mod 重复", "同一个 Mod 放了多个版本，删掉多余的 jar。"},
            {"Mixin apply failed", "Mixin 注入失败", "常见于 Mod 与游戏版本不匹配，逐一停用最近添加的 Mod。"},
            {"MixinApplyError", "Mixin 注入失败", "常见于 Mod 与游戏版本不匹配，逐一停用最近添加的 Mod。"},
            {"java.lang.ClassNotFoundException", "缺少类 / 依赖", "确认 Forge / Fabric 等加载器安装完整，必要时修复游戏文件。"},
            {"NoSuchMethodError", "Mod 与游戏版本不匹配", "把 Mod 换成对应游戏版本的版本。"},
            {"GLFW error", "图形环境异常", "更新显卡驱动；笔记本切换到独显运行。"},
            {"Pixel format not accelerated", "显卡驱动不支持 OpenGL", "安装厂商驱动，不要用系统自带的基础驱动。"},
            {"Failed to create window", "无法创建游戏窗口", "检查显卡驱动与远程桌面 / 虚拟机环境。"},
            {"AccessDeniedException", "文件被占用或无权限", "关闭占用文件的程序，或以管理员身份运行启动器。"},
            {"The game crashed whilst", "游戏崩溃", "结合下方错误行定位；也可以在「日志」里换崩溃报告查看完整堆栈。"},
            {"Exception in thread \"main\"", "主线程异常", "查看紧随其后的 Caused by 行。"},
            {"Could not find or load main class", "主类缺失", "版本文件不完整，重新下载该版本。"},
            {"java.lang.NoClassDefFoundError", "缺少类 / 依赖", "确认加载器安装完整，必要时重新下载版本。"},
            {"Failed to download", "下载失败", "检查网络或切换下载源后重试。"},
            {"java.net.UnknownHostException", "网络不可用", "检查网络连接、代理与 DNS 设置。"},
            {"java.net.ConnectException", "连接被拒绝", "检查代理设置与防火墙。"},
            {"Incompatible mod set", "Mod 组合不兼容", "按日志列出的冲突 Mod 逐个停用排查。"},
    };

    private CrashAnalyzer() {
    }

    public static Result analyze(List<String> lines) {
        List<Hit> hits = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        if (lines == null) return new Result(hits, fallback);
        String[] ruleLows = new String[RULES.length];
        String[] ruleFound = new String[RULES.length];
        for (int i = 0; i < RULES.length; i++) {
            ruleLows[i] = RULES[i][0].toLowerCase(java.util.Locale.ROOT);
        }
        for (String line : lines) {
            if (line == null) continue;
            String low = line.toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < ruleLows.length; i++) {
                if (ruleFound[i] == null && low.contains(ruleLows[i])) {
                    ruleFound[i] = line.trim();
                }
            }
        }
        for (int i = 0; i < RULES.length; i++) {
            if (ruleFound[i] != null) hits.add(new Hit(RULES[i][1], RULES[i][2], ruleFound[i]));
        }
        int count = 0;
        for (String line : lines) {
            if (line == null) continue;
            String low = line.toLowerCase(java.util.Locale.ROOT);
            boolean err = low.contains("error") || low.contains("exception")
                    || low.contains("fatal") || low.contains("crash") || low.contains("/error");
            if (err) {
                fallback.add(line.trim());
                if (++count >= 6) break;
            }
        }
        return new Result(hits, fallback);
    }

    public static Result analyzeInstance(Path runDir, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (runDir != null) {
            Path crashDir = runDir.resolve("crash-reports");
            if (Files.isDirectory(crashDir)) {
                try (var s = Files.list(crashDir)) {
                    Path newest = s.filter(Files::isRegularFile)
                            .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified()))
                            .orElse(null);
                    if (newest != null) lines.addAll(tail(newest, maxLines));
                } catch (Exception ignored) {
                }
            }
            Path latest = runDir.resolve("logs").resolve("latest.log");
            if (Files.isRegularFile(latest)) {
                List<String> tail = tail(latest, maxLines);
                java.util.Set<String> seen = new java.util.HashSet<>(lines);
                for (String l : tail) {
                    if (seen.add(l)) lines.add(l);
                }
            }
        }
        return analyze(lines);
    }

    private static List<String> tail(Path file, int maxLines) {
        List<String> out = new ArrayList<>();
        try {
            long size = Files.size(file);
            long from = Math.max(0, size - 4L * 1024 * 1024);
            try (var raf = new java.io.RandomAccessFile(file.toFile(), "r")) {
                raf.seek(from);
                byte[] data = new byte[(int) (size - from)];
                raf.readFully(data);
                String text = new String(data, StandardCharsets.UTF_8);
                String[] all = text.split("\\R");
                int start = Math.max(0, all.length - maxLines);
                for (int i = start; i < all.length; i++) out.add(all[i]);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String describe(Result r) {
        if (r == null || !r.found()) {
            StringBuilder sb = new StringBuilder(Lang.t("没有匹配到已知的崩溃特征。"));
            if (r != null && !r.fallback().isEmpty()) {
                sb.append(System.lineSeparator()).append(System.lineSeparator())
                        .append(Lang.t("最近的错误行：")).append(System.lineSeparator());
                for (String line : r.fallback()) sb.append("    ").append(line).append(System.lineSeparator());
            } else {
                sb.append(System.lineSeparator()).append(System.lineSeparator())
                        .append(Lang.t("这份日志里也没有错误级别的行。"));
            }
            return sb.toString();
        }
        StringBuilder hits = new StringBuilder();
        StringBuilder evidence = new StringBuilder();
        for (Hit h : r.hits()) {
            hits.append("· ").append(Lang.t(h.cause())).append("：").append(Lang.t(h.advice()))
                    .append(System.lineSeparator());
            evidence.append("    ").append(h.evidence()).append(System.lineSeparator());
        }
        return Lang.t("可能的原因与建议：") + System.lineSeparator() + hits
                + System.lineSeparator() + Lang.t("证据（日志原文）：") + System.lineSeparator() + evidence;
    }
}
