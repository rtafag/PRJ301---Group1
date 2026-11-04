package com.group1.ast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SourceLoader {
    public static final int MAX_JAVA_FILES = 300;
    public static final int MAX_ARCHIVE_ENTRIES = 1000;
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;

    private static final Set<String> IGNORED_DIRECTORIES = new HashSet<>(
            Arrays.asList(".git", ".idea", "target", "build", "node_modules", "__MACOSX"));

    private SourceLoader() {
    }

    public static final class LoadResult {
        public final List<AstModel.SourceFile> files = new ArrayList<>();
        public final List<AstModel.SkippedFile> skipped = new ArrayList<>();
    }

    public static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isDirectory(path) || name.endsWith(".java") || name.endsWith(".zip");
    }

    public static LoadResult load(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (Files.isDirectory(path)) {
            return fromDirectory(path);
        }
        if (name.endsWith(".zip")) {
            return fromZip(path);
        }
        if (name.endsWith(".java")) {
            return fromSingleFile(path);
        }
        throw new IOException("Unsupported source type: " + path.getFileName());
    }

    private static LoadResult fromSingleFile(Path path) throws IOException {
        LoadResult result = new LoadResult();
        String fileName = path.getFileName().toString();
        if (Files.size(path) > MAX_FILE_BYTES) {
            result.skipped.add(new AstModel.SkippedFile(fileName, "file too large"));
        } else {
            result.files.add(new AstModel.SourceFile(fileName, decode(Files.readAllBytes(path))));
        }
        return result;
    }

    private static LoadResult fromDirectory(Path root) throws IOException {
        LoadResult result = new LoadResult();
        List<Path> candidates;
        try (Stream<Path> walk = Files.walk(root)) {
            candidates = walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(p -> !hasIgnoredSegment(root.relativize(p)))
                    .sorted()
                    .collect(Collectors.toList());
        }
        long total = 0;
        for (Path file : candidates) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (result.files.size() >= MAX_JAVA_FILES) {
                result.skipped.add(new AstModel.SkippedFile(relative, "java file limit reached"));
                continue;
            }
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                result.skipped.add(new AstModel.SkippedFile(relative, "file too large"));
                continue;
            }
            total += size;
            if (total > MAX_TOTAL_BYTES) {
                throw new IOException("Source directory exceeds the total size limit.");
            }
            result.files.add(new AstModel.SourceFile(relative, decode(Files.readAllBytes(file))));
        }
        return result;
    }

    private static LoadResult fromZip(Path archive) throws IOException {
        LoadResult result = new LoadResult();
        Set<String> seen = new HashSet<>();
        int entries = 0;
        long total = 0;
        try (InputStream raw = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("Archive contains too many entries.");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                boolean java = name.toLowerCase(Locale.ROOT).endsWith(".java");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                boolean tooLarge = false;
                int read;
                while ((read = zip.read(chunk)) != -1) {
                    total += read;
                    if (total > MAX_TOTAL_BYTES) {
                        throw new IOException("Archive exceeds the total uncompressed size limit.");
                    }
                    if (java && !tooLarge) {
                        if (buffer.size() + read > MAX_FILE_BYTES) {
                            tooLarge = true;
                        } else {
                            buffer.write(chunk, 0, read);
                        }
                    }
                }
                if (!java || isIgnoredEntry(name)) {
                    continue;
                }
                if (!isSafePath(name)) {
                    result.skipped.add(new AstModel.SkippedFile(name, "unsafe path"));
                } else if (tooLarge) {
                    result.skipped.add(new AstModel.SkippedFile(name, "file too large"));
                } else if (!seen.add(name)) {
                    result.skipped.add(new AstModel.SkippedFile(name, "duplicate entry"));
                } else if (result.files.size() >= MAX_JAVA_FILES) {
                    result.skipped.add(new AstModel.SkippedFile(name, "java file limit reached"));
                } else {
                    result.files.add(new AstModel.SourceFile(name, decode(buffer.toByteArray())));
                }
            }
        }
        result.files.sort((a, b) -> a.path.compareTo(b.path));
        return result;
    }

    private static boolean isSafePath(String name) {
        if (name.isEmpty() || name.startsWith("/") || name.contains(":")) {
            return false;
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIgnoredEntry(String name) {
        for (String segment : name.split("/")) {
            if (IGNORED_DIRECTORIES.contains(segment) || segment.startsWith("._")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIgnoredSegment(Path relative) {
        for (Path segment : relative) {
            if (IGNORED_DIRECTORIES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String decode(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
