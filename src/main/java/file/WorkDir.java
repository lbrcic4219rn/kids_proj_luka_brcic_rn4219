package file;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class WorkDir {
    public static final String CONFLICT_SUFFIX = ".conflict";

    private final Path root;
    private final Map<String, Tracked> tracked = new ConcurrentHashMap<>();

    public WorkDir(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public static String normalize(String name) {
        String normalized = name.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.equals(".") ? "" : normalized;
    }

    private Path resolve(String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path is outside of the work root: " + path);
        }
        return resolved;
    }

    public List<String> listFiles(String name) {
        Path start = resolve(name);
        if (Files.isRegularFile(start)) {
            return List.of(relative(start));
        }
        if (!Files.isDirectory(start)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(start)) {
            List<String> result = new ArrayList<>(walk.filter(Files::isRegularFile).map(this::relative)
                    .filter(path -> !path.endsWith(CONFLICT_SUFFIX)).toList());
            result.sort(null);
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String relative(Path file) {
        return root.relativize(file).toString().replace(File.separatorChar, '/');
    }

    public boolean exists(String path) {
        return Files.isRegularFile(resolve(path));
    }

    public String read(String path) {
        try {
            return Files.readString(resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void write(String path, String content) {
        try {
            Path file = resolve(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(String path) {
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void track(String path, int version, String content) {
        tracked.put(path, new Tracked(version, content));
    }

    public void untrack(String path) {
        tracked.remove(path);
    }

    public int baseVersion(String path) {
        Tracked entry = tracked.get(path);
        return entry == null ? -1 : entry.version();
    }

    public boolean isModified(String path) {
        Tracked entry = tracked.get(path);
        return entry == null || !entry.content().equals(read(path));
    }

    public List<String> trackedUnder(String name) {
        List<String> result = new ArrayList<>();
        for (String path : tracked.keySet()) {
            if (name.isEmpty() || path.equals(name) || path.startsWith(name + "/")) {
                result.add(path);
            }
        }
        result.sort(null);
        return result;
    }

    private record Tracked(int version, String content) {
    }
}
