package file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

public class Storage {
    private final Path root;
    private final Map<String, TreeSet<Integer>> versions = new HashMap<>();
    private final Set<String> teamCopies = new HashSet<>();

    public Storage(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    private Path fileOf(String path, int version) {
        return root.resolve(path + ".v" + version);
    }

    public synchronized void store(String path, int version, String content) {
        try {
            Path file = fileOf(path, version);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            versions.computeIfAbsent(path, key -> new TreeSet<>()).add(version);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized String read(String path, int version) {
        if (!versions.getOrDefault(path, new TreeSet<>()).contains(version)) {
            return null;
        }
        try {
            return Files.readString(fileOf(path, version));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized SortedMap<Integer, String> readAll(String path) {
        SortedMap<Integer, String> result = new TreeMap<>();
        for (int version : versionsOf(path)) {
            result.put(version, read(path, version));
        }
        return result;
    }

    public synchronized void delete(String path) {
        try {
            for (int version : versionsOf(path)) {
                Files.deleteIfExists(fileOf(path, version));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        versions.remove(path);
        teamCopies.remove(path);
    }

    public synchronized List<String> paths() {
        List<String> result = new ArrayList<>(versions.keySet());
        result.sort(null);
        return result;
    }

    public synchronized List<Integer> versionsOf(String path) {
        return new ArrayList<>(versions.getOrDefault(path, new TreeSet<>()));
    }

    public synchronized void markTeamCopy(String path) {
        teamCopies.add(path);
    }

    public synchronized boolean isTeamCopy(String path) {
        return teamCopies.contains(path);
    }
}
