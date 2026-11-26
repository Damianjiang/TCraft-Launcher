package tcraft.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/// File-system helpers for plugins. All paths use `java.nio.file.Path`.
///
/// Plugins run with the same file permissions as the launcher. Keep writes inside
/// `ctx.dataDir()` unless the user explicitly selected another path.
public final class Files {

    /// Reads a UTF-8 text file.
    public String readText(Path path) throws IOException {
        return java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
    }

    /// Reads a text file with an explicit charset.
    public String readText(Path path, Charset charset) throws IOException {
        return java.nio.file.Files.readString(path, charset == null ? StandardCharsets.UTF_8 : charset);
    }

    /// Reads all lines from a UTF-8 text file.
    public List<String> readLines(Path path) throws IOException {
        return java.nio.file.Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    /// Writes UTF-8 text and creates missing parent directories.
    public void writeText(Path path, String text) throws IOException {
        createParent(path);
        java.nio.file.Files.writeString(path, text == null ? "" : text, StandardCharsets.UTF_8);
    }

    /// Writes UTF-8 lines and creates missing parent directories.
    public void writeLines(Path path, List<String> lines) throws IOException {
        createParent(path);
        java.nio.file.Files.write(path, lines == null ? List.of() : lines, StandardCharsets.UTF_8);
    }

    /// Appends UTF-8 text, creating the file when needed.
    public void appendText(Path path, String text) throws IOException {
        createParent(path);
        java.nio.file.Files.writeString(path, text == null ? "" : text, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    /// Reads a file as bytes.
    public byte[] readBytes(Path path) throws IOException {
        return java.nio.file.Files.readAllBytes(path);
    }

    /// Writes bytes and creates missing parent directories.
    public void writeBytes(Path path, byte[] data) throws IOException {
        createParent(path);
        java.nio.file.Files.write(path, data == null ? new byte[0] : data);
    }

    /// Tests whether a path exists.
    public boolean exists(Path path) {
        return java.nio.file.Files.exists(path);
    }

    /// Tests whether a path is a directory.
    public boolean isDirectory(Path path) {
        return java.nio.file.Files.isDirectory(path);
    }

    /// Tests whether a path is a regular file.
    public boolean isRegularFile(Path path) {
        return java.nio.file.Files.isRegularFile(path);
    }

    /// Lists one directory level, sorted by file name.
    public List<Path> list(Path directory) throws IOException {
        if (!java.nio.file.Files.isDirectory(directory)) return List.of();
        try (var stream = java.nio.file.Files.list(directory)) {
            return stream.sorted(Comparator.comparing(p -> p.getFileName().toString()
                    .toLowerCase(java.util.Locale.ROOT))).toList();
        }
    }

    /// Walks a directory up to `maxDepth` levels.
    public List<Path> walk(Path directory, int maxDepth) throws IOException {
        if (!java.nio.file.Files.isDirectory(directory)) return List.of();
        try (var stream = java.nio.file.Files.walk(directory, Math.max(0, maxDepth))) {
            return stream.toList();
        }
    }

    /// Creates a directory and all missing parents.
    public Path createDirectories(Path directory) throws IOException {
        return java.nio.file.Files.createDirectories(directory);
    }

    /// Creates an empty file and missing parents.
    public Path createFile(Path path) throws IOException {
        createParent(path);
        return java.nio.file.Files.createFile(path);
    }

    /// Returns the file size in bytes, or `0` when the path is missing.
    public long size(Path path) throws IOException {
        return java.nio.file.Files.exists(path) ? java.nio.file.Files.size(path) : 0L;
    }

    /// Returns the last modified time.
    public Instant lastModified(Path path) throws IOException {
        return java.nio.file.Files.getLastModifiedTime(path).toInstant();
    }

    /// Sets the last modified time.
    public void setLastModified(Path path, Instant instant) throws IOException {
        java.nio.file.Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.from(instant));
    }

    /// Copies a file or directory. Directory copies are recursive.
    public void copy(Path source, Path target, boolean replace) throws IOException {
        if (java.nio.file.Files.isDirectory(source)) {
            copyDirectory(source, target, replace);
            return;
        }
        createParent(target);
        if (replace) {
            java.nio.file.Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        } else {
            java.nio.file.Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    /// Moves a file or directory, creating the target parent.
    public void move(Path source, Path target, boolean replace) throws IOException {
        createParent(target);
        if (replace) {
            java.nio.file.Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } else {
            java.nio.file.Files.move(source, target);
        }
    }

    /// Deletes one file or an empty directory.
    public boolean delete(Path path) throws IOException {
        return java.nio.file.Files.deleteIfExists(path);
    }

    /// Deletes a file tree without following symbolic links.
    public void deleteRecursive(Path path) throws IOException {
        if (!java.nio.file.Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        java.nio.file.Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                java.nio.file.Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                java.nio.file.Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /// Creates a temporary file under the system temp directory.
    public Path tempFile(String prefix, String suffix) throws IOException {
        return java.nio.file.Files.createTempFile(prefix == null ? "tcraft-" : prefix,
                suffix == null ? ".tmp" : suffix);
    }

    /// Creates a temporary directory under the system temp directory.
    public Path tempDirectory(String prefix) throws IOException {
        return java.nio.file.Files.createTempDirectory(prefix == null ? "tcraft-" : prefix);
    }

    /// Creates a UTF-8 zip archive from a file or directory.
    public void zip(Path source, Path zipFile) throws IOException {
        if (!java.nio.file.Files.exists(source)) throw new IOException("source does not exist: " + source);
        createParent(zipFile);
        try (ZipOutputStream out = new ZipOutputStream(java.nio.file.Files.newOutputStream(zipFile))) {
            if (java.nio.file.Files.isDirectory(source)) {
                Path root = source.toAbsolutePath().normalize();
                java.nio.file.Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String name = root.relativize(file).toString().replace('\\', '/');
                        out.putNextEntry(new ZipEntry(name));
                        java.nio.file.Files.copy(file, out);
                        out.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                out.putNextEntry(new ZipEntry(source.getFileName().toString()));
                java.nio.file.Files.copy(source, out);
                out.closeEntry();
            }
        }
    }

    /// Extracts a zip into a directory and rejects entries outside the target.
    public void unzip(Path zipFile, Path target) throws IOException {
        java.nio.file.Files.createDirectories(target);
        Path root = target.toAbsolutePath().normalize();
        int entries = 0;
        long total = 0;
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                entries++;
                if (entries > 100_000) throw new IOException("zip contains too many entries");
                Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root)) throw new IOException("zip entry escapes target: " + entry.getName());
                if (entry.isDirectory()) {
                    java.nio.file.Files.createDirectories(out);
                    continue;
                }
                createParent(out);
                try (InputStream in = zip.getInputStream(entry); OutputStream os = java.nio.file.Files.newOutputStream(out)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        total += n;
                        if (total > 2L * 1024 * 1024 * 1024) throw new IOException("zip expands beyond 2 GiB");
                        os.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    private static void createParent(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) java.nio.file.Files.createDirectories(parent);
    }

    private static void copyDirectory(Path source, Path target, boolean replace) throws IOException {
        Path root = source.toAbsolutePath().normalize();
        java.nio.file.Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                java.nio.file.Files.createDirectories(target.resolve(root.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path out = target.resolve(root.relativize(file));
                if (replace) {
                    java.nio.file.Files.copy(file, out, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    java.nio.file.Files.copy(file, out, StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
