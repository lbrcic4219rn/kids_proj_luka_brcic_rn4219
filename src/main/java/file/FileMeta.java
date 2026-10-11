package file;

import app.ServantInfo;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class FileMeta implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String path;
    private final Map<String, Set<String>> pullers = new HashMap<>();
    private final Map<String, ServantInfo> teamHolders = new HashMap<>();
    private int version;
    private boolean deleted;
    private long stamp;

    public FileMeta(String path) {
        this.path = path;
    }

    public FileMeta copy() {
        FileMeta copy = new FileMeta(path);
        copy.version = version;
        copy.deleted = deleted;
        copy.stamp = stamp;
        pullers.forEach((team, nodes) -> copy.pullers.put(team, new HashSet<>(nodes)));
        copy.teamHolders.putAll(teamHolders);
        return copy;
    }

    @Override
    public String toString() {
        if (deleted) {
            return path + " (removed)";
        }
        StringBuilder text = new StringBuilder(path).append(" v").append(version);
        pullers.forEach((team, nodes) -> {
            if (!nodes.isEmpty()) {
                text.append(" | team ").append(team).append(": ").append(nodes.size()).append(" working");
            }
        });
        teamHolders.forEach((team, holder) -> text.append(" | team ").append(team).append(" copy at ").append(holder.getKey()));
        return text.toString();
    }
}
