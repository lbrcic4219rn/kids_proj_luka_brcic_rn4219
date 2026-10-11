package file;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FileIndex implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, FileMeta> entries = new HashMap<>();

    public synchronized FileMeta get(String path) {
        FileMeta meta = entries.get(path);
        return meta == null ? null : meta.copy();
    }

    public synchronized boolean apply(FileMeta meta) {
        FileMeta current = entries.get(meta.getPath());
        if (current != null && current.getStamp() >= meta.getStamp()) {
            return false;
        }
        entries.put(meta.getPath(), meta.copy());
        return true;
    }

    public void merge(FileIndex other) {
        other.all().forEach(this::apply);
    }

    public synchronized List<FileMeta> all() {
        List<FileMeta> result = new ArrayList<>();
        entries.values().forEach(meta -> result.add(meta.copy()));
        result.sort(Comparator.comparing(FileMeta::getPath));
        return result;
    }

    public synchronized List<String> livePathsUnder(String name) {
        List<String> result = new ArrayList<>();
        for (FileMeta meta : entries.values()) {
            String path = meta.getPath();
            boolean matches = name.isEmpty() || path.equals(name) || path.startsWith(name + "/");
            if (matches && !meta.isDeleted()) {
                result.add(path);
            }
        }
        result.sort(null);
        return result;
    }

    public synchronized int digest() {
        int digest = 0;
        for (FileMeta meta : entries.values()) {
            digest += Objects.hash(meta.getPath(), meta.getStamp());
        }
        return digest;
    }

    public synchronized long maxStamp() {
        return entries.values().stream().mapToLong(FileMeta::getStamp).max().orElse(0);
    }

    public synchronized FileIndex copy() {
        FileIndex copy = new FileIndex();
        entries.forEach((path, meta) -> copy.entries.put(path, meta.copy()));
        return copy;
    }

    @Serial
    private synchronized void writeObject(ObjectOutputStream output) throws IOException {
        output.defaultWriteObject();
    }
}
