package file;

import app.AppConfig;
import app.ServantInfo;
import app.SystemState;
import servant.message.Message;
import servant.message.MessageType;
import servant.message.Payloads.FileBundle;
import servant.message.Payloads.FileData;
import servant.message.Payloads.FileRef;
import servant.message.util.MessageUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class FileService {
    private static final long REQUEST_TIMEOUT_MS = 5000;
    private static final String COMMIT = "commit: '";
    private static final String PULL = "pull: '";
    private static final String INDENT = "\n    ";

    public void add(String name) {
        List<String> files = AppConfig.workDir().listFiles(name);
        if (files.isEmpty()) {
            AppConfig.timestampedErrorPrint("add: there is no file or directory '" + name + "' in the work root.");
            return;
        }
        AppConfig.mutex().lock();
        try {
            pruneTeamInfo();
            for (String path : files) {
                addFile(path);
            }
        } finally {
            AppConfig.mutex().unlock();
        }
    }

    private void addFile(String path) {
        FileMeta existing = AppConfig.mutex().heldIndex().get(path);
        if (existing != null && !existing.isDeleted()) {
            AppConfig.timestampedErrorPrint("add: '" + path + "' already exists in the system (version "
                    + existing.getVersion() + "). Not added.");
            return;
        }
        String content = AppConfig.workDir().read(path);
        FileMeta meta = new FileMeta(path);
        if (failsToStore(path, 0, content, meta)) {
            AppConfig.timestampedErrorPrint("add: couldn't store '" + path + "' on any node.");
            return;
        }
        workingWith(meta);
        publish(meta);
        AppConfig.workDir().track(path, 0, content);
        AppConfig.timestampedStandardPrint("add: '" + path + "' added to the system as version 0.");
    }

    public void pull(String name, Integer version) {
        AppConfig.mutex().lock();
        try {
            pruneTeamInfo();
            List<String> paths = AppConfig.mutex().heldIndex().livePathsUnder(name);
            if (paths.isEmpty()) {
                AppConfig.timestampedErrorPrint(PULL + name + "' does not exist in the system.");
                return;
            }
            for (String path : paths) {
                pullFile(path, version);
            }
        } finally {
            AppConfig.mutex().unlock();
        }
    }

    private void pullFile(String path, Integer requestedVersion) {
        FileMeta meta = AppConfig.mutex().heldIndex().get(path);
        int version = requestedVersion == null ? meta.getVersion() : requestedVersion;
        if (version < 0 || version > meta.getVersion()) {
            AppConfig.timestampedErrorPrint(PULL + path + "' has no version " + version
                    + " (latest is " + meta.getVersion() + ").");
            return;
        }
        String content = fetch(path, version, meta);
        if (content == null) {
            AppConfig.timestampedErrorPrint("pull: couldn't fetch '" + path + "' version " + version + " from any node.");
            return;
        }
        AppConfig.workDir().write(path, content);
        AppConfig.workDir().track(path, version, content);
        AppConfig.timestampedStandardPrint(PULL + path + "' version " + version + " is now in the work root.");

        boolean changed = workingWith(meta);
        changed |= createTeamCopyIfNeeded(meta, version, content);
        if (changed) {
            publish(meta);
        }
    }

    public void commit(String name, Supplier<String> input) {
        List<String> files = AppConfig.workDir().trackedUnder(name);
        if (files.isEmpty()) {
            AppConfig.timestampedErrorPrint(COMMIT + name + "' is not tracked. Use add or pull first.");
            return;
        }
        List<String> conflicts = new ArrayList<>();
        AppConfig.mutex().lock();
        try {
            pruneTeamInfo();
            for (String path : files) {
                if (commitFile(path)) {
                    conflicts.add(path);
                }
            }
        } finally {
            AppConfig.mutex().unlock();
        }
        for (String path : conflicts) {
            resolveConflict(path, input);
        }
    }

    private boolean commitFile(String path) {
        if (!AppConfig.workDir().exists(path)) {
            AppConfig.timestampedErrorPrint(COMMIT + path + "' is missing from the work root.");
            return false;
        }
        FileMeta meta = AppConfig.mutex().heldIndex().get(path);
        if (meta == null || meta.isDeleted()) {
            AppConfig.timestampedErrorPrint(COMMIT + path + "' was removed from the system.");
            return false;
        }
        int base = AppConfig.workDir().baseVersion(path);
        if (!AppConfig.workDir().isModified(path)) {
            AppConfig.timestampedStandardPrint(COMMIT + path + "' is not modified, version stays " + base + ".");
            return false;
        }
        if (meta.getVersion() > base) {
            AppConfig.timestampedErrorPrint("commit: CONFLICT on '" + path + "' - you started from version " + base
                    + ", the system already has version " + meta.getVersion() + ".");
            return true;
        }
        commitVersion(path, meta);
        return false;
    }

    private void commitVersion(String path, FileMeta meta) {
        String content = AppConfig.workDir().read(path);
        int version = meta.getVersion() + 1;
        if (failsToStore(path, version, content, meta)) {
            AppConfig.timestampedErrorPrint("commit: couldn't store '" + path + "' on any node.");
            return;
        }
        meta.setVersion(version);
        publish(meta);
        AppConfig.workDir().track(path, version, content);
        AppConfig.timestampedStandardPrint(COMMIT + path + "' is now version " + version + ".");
    }

    private void resolveConflict(String path, Supplier<String> input) {
        while (true) {
            AppConfig.timestampedStandardPrint("Conflict on '" + path + "'. Choose: view | push | pull");
            String choice = input.get();
            if (choice == null) {
                AppConfig.timestampedErrorPrint("No input, conflict on '" + path + "' left unresolved.");
                return;
            }
            switch (choice.trim()) {
                case "view" -> viewSystemVersion(path);
                case "push" -> {
                    pushLocalVersion(path);
                    return;
                }
                case "pull" -> {
                    pull(path, null);
                    AppConfig.workDir().delete(path + WorkDir.CONFLICT_SUFFIX);
                    return;
                }
                default -> AppConfig.timestampedErrorPrint("Unknown choice: " + choice.trim());
            }
        }
    }

    private void viewSystemVersion(String path) {
        AppConfig.mutex().lock();
        try {
            FileMeta meta = AppConfig.mutex().heldIndex().get(path);
            String content = meta == null || meta.isDeleted() ? null : fetch(path, meta.getVersion(), meta);
            if (content == null) {
                AppConfig.timestampedErrorPrint("view: couldn't fetch '" + path + "' from the system.");
                return;
            }
            String temporary = path + WorkDir.CONFLICT_SUFFIX;
            AppConfig.workDir().write(temporary, content);
            AppConfig.timestampedStandardPrint("view: version " + meta.getVersion() + " from the system is saved as '"
                    + temporary + "':\n" + content);
        } finally {
            AppConfig.mutex().unlock();
        }
    }

    private void pushLocalVersion(String path) {
        AppConfig.mutex().lock();
        try {
            FileMeta meta = AppConfig.mutex().heldIndex().get(path);
            if (meta == null || meta.isDeleted()) {
                AppConfig.timestampedErrorPrint("push: '" + path + "' was removed from the system.");
                return;
            }
            commitVersion(path, meta);
            AppConfig.workDir().delete(path + WorkDir.CONFLICT_SUFFIX);
        } finally {
            AppConfig.mutex().unlock();
        }
    }

    public void remove(String name) {
        AppConfig.mutex().lock();
        try {
            pruneTeamInfo();
            List<String> paths = AppConfig.mutex().heldIndex().livePathsUnder(name);
            if (paths.isEmpty()) {
                AppConfig.timestampedErrorPrint("remove: '" + name + "' does not exist in the system.");
                return;
            }
            for (String path : paths) {
                FileMeta meta = AppConfig.mutex().heldIndex().get(path);
                meta.setDeleted(true);
                meta.getPullers().clear();
                meta.getTeamHolders().clear();
                publish(meta);
                AppConfig.workDir().untrack(path);
                AppConfig.timestampedStandardPrint("remove: '" + path + "' removed from the system.");
            }
        } finally {
            AppConfig.mutex().unlock();
        }
    }

    private void publish(FileMeta meta) {
        meta.setStamp(AppConfig.mutex().nextStamp());
        AppConfig.mutex().heldIndex().apply(meta);
        applyToReplica(meta);
        MessageUtil.broadcast(MessageType.UPDATE_INDEX, meta.copy());
    }

    public void onIndexUpdate(FileMeta meta) {
        applyToReplica(meta);
    }

    public void mergeIndex(FileIndex other) {
        for (FileMeta meta : other.all()) {
            applyToReplica(meta);
        }
    }

    private void applyToReplica(FileMeta meta) {
        boolean applied = AppConfig.index().apply(meta);
        if ((applied || meta.getStamp() == stampOf(meta.getPath())) && meta.isDeleted()) {
            AppConfig.storage().delete(meta.getPath());
        }
    }

    private long stampOf(String path) {
        FileMeta current = AppConfig.index().get(path);
        return current == null ? -1 : current.getStamp();
    }

    private boolean workingWith(FileMeta meta) {
        return meta.getPullers().computeIfAbsent(AppConfig.me().getTeam(), team -> new HashSet<>())
                .add(AppConfig.me().getId());
    }

    private boolean createTeamCopyIfNeeded(FileMeta meta, int pulledVersion, String pulledContent) {
        String team = AppConfig.me().getTeam();
        int working = meta.getPullers().get(team).size();
        if (working <= AppConfig.teamLimit() || meta.getTeamHolders().containsKey(team)) {
            return false;
        }
        String latest = pulledVersion == meta.getVersion() ? pulledContent : fetch(meta.getPath(), meta.getVersion(), meta);
        if (latest == null) {
            return false;
        }
        AppConfig.storage().store(meta.getPath(), meta.getVersion(), latest);
        AppConfig.storage().markTeamCopy(meta.getPath());
        meta.getTeamHolders().put(team, AppConfig.me());
        AppConfig.timestampedStandardPrint("TEAM OPTIMISATION: " + working + " members of team '" + team
                + "' work with '" + meta.getPath() + "' (limit " + AppConfig.teamLimit()
                + "). Team copy created on this node.");
        return true;
    }

    private void pruneTeamInfo() {
        SystemState state = AppConfig.state();
        Set<String> alive = new HashSet<>();
        state.getMembers().forEach(node -> alive.add(node.getId()));
        for (FileMeta meta : AppConfig.mutex().heldIndex().all()) {
            boolean changed = false;
            for (Set<String> nodes : meta.getPullers().values()) {
                changed |= nodes.retainAll(alive);
            }
            changed |= meta.getTeamHolders().values().removeIf(holder -> !state.isMember(holder));
            if (changed) {
                publish(meta);
            }
        }
    }

    private boolean failsToStore(String path, int version, String content, FileMeta meta) {
        ServantInfo teamHolder = meta.getTeamHolders().get(AppConfig.me().getTeam());
        if (teamHolder != null && !teamHolder.equals(AppConfig.me()) && AppConfig.state().isMember(teamHolder)) {
            Message ack = MessageUtil.request(MessageType.ADD_FILE, teamHolder,
                    new FileData(path, version, content, true), REQUEST_TIMEOUT_MS);
            if (ack != null) {
                AppConfig.timestampedStandardPrint("'" + path + "' v" + version + " stored through the team copy at "
                        + teamHolder + ".");
                return false;
            }
            AppConfig.timestampedErrorPrint("Team copy at " + teamHolder + " does not respond, using the global copy.");
        }
        return distribute(new FileData(path, version, content, false), meta) == 0;
    }

    private int distribute(FileData data, FileMeta meta) {
        Set<ServantInfo> targets = new LinkedHashSet<>(AppConfig.state().holdersFor(data.path()));
        if (meta != null) {
            meta.getTeamHolders().values().stream().filter(AppConfig.state()::isMember).forEach(targets::add);
        }
        int stored = 0;
        List<CompletableFuture<Message>> pending = new ArrayList<>();
        for (ServantInfo target : targets) {
            if (target.equals(AppConfig.me())) {
                AppConfig.storage().store(data.path(), data.version(), data.content());
                stored++;
            } else {
                pending.add(MessageUtil.requestAsync(MessageType.ADD_FILE, target, data, REQUEST_TIMEOUT_MS, false));
            }
        }
        for (CompletableFuture<Message> future : pending) {
            if (MessageUtil.await(future) != null) {
                stored++;
            }
        }
        return stored;
    }

    public void onStore(Message request) {
        FileData data = request.payload();
        AppConfig.storage().store(data.path(), data.version(), data.content());
        MessageUtil.reply(request, MessageType.FILE_STORED, null);
        if (data.propagate()) {
            AppConfig.storage().markTeamCopy(data.path());
            AppConfig.timestampedStandardPrint("Team copy of '" + data.path() + "' updated to v" + data.version()
                    + ", updating the global copy.");
            distribute(new FileData(data.path(), data.version(), data.content(), false), AppConfig.index().get(data.path()));
        }
    }

    private String fetch(String path, int version, FileMeta meta) {
        Set<ServantInfo> candidates = new LinkedHashSet<>();
        ServantInfo teamHolder = meta.getTeamHolders().get(AppConfig.me().getTeam());
        if (teamHolder != null && AppConfig.state().isMember(teamHolder)) {
            candidates.add(teamHolder);
        }
        candidates.addAll(AppConfig.state().holdersFor(path));
        candidates.addAll(AppConfig.state().getMembers());
        for (ServantInfo candidate : candidates) {
            String content;
            if (candidate.equals(AppConfig.me())) {
                content = AppConfig.storage().read(path, version);
            } else {
                Message reply = MessageUtil.request(MessageType.REQUEST_FILE, candidate, new FileRef(path, version),
                        REQUEST_TIMEOUT_MS);
                content = reply == null ? null : reply.<FileData>payload().content();
            }
            if (content != null) {
                AppConfig.timestampedStandardPrint("'" + path + "' v" + version + " served by " + candidate
                        + (candidate.equals(teamHolder) ? " (TEAM COPY)." : "."));
                return content;
            }
        }
        return null;
    }

    public void onFileRequest(Message request) {
        FileRef ref = request.payload();
        String content = AppConfig.storage().read(ref.path(), ref.version());
        MessageUtil.reply(request, MessageType.GIVE_FILE, new FileData(ref.path(), ref.version(), content, false));
    }

    public void rebalance(List<ServantInfo> before, List<ServantInfo> after) {
        for (String path : AppConfig.storage().paths()) {
            FileMeta meta = AppConfig.index().get(path);
            if (meta != null && meta.isDeleted()) {
                AppConfig.storage().delete(path);
            } else {
                rebalanceFile(path, SystemState.holdersFor(path, before), SystemState.holdersFor(path, after));
            }
        }
    }

    private void rebalanceFile(String path, List<ServantInfo> oldHolders, List<ServantInfo> newHolders) {
        ServantInfo me = AppConfig.me();
        List<ServantInfo> gained = new ArrayList<>(newHolders);
        gained.removeAll(oldHolders);
        gained.remove(me);
        if (!gained.isEmpty()) {
            TreeMap<Integer, String> versions = new TreeMap<>(AppConfig.storage().readAll(path));
            for (ServantInfo holder : gained) {
                AppConfig.timestampedStandardPrint("RESTRUCTURING: sending '" + path + "' to its new holder " + holder + ".");
                MessageUtil.send(MessageType.REPLICATE_FILES, holder, new FileBundle(path, versions));
            }
        }
        if (!newHolders.contains(me) && !AppConfig.storage().isTeamCopy(path)) {
            AppConfig.storage().delete(path);
        }
    }

    public void onReplicate(FileBundle bundle) {
        FileMeta meta = AppConfig.index().get(bundle.path());
        if (meta != null && meta.isDeleted()) {
            return;
        }
        bundle.versions().forEach((version, content) -> AppConfig.storage().store(bundle.path(), version, content));
    }

    public String describe() {
        StringBuilder text = new StringBuilder("Virtual file system:");
        List<FileMeta> entries = AppConfig.index().all();
        entries.removeIf(FileMeta::isDeleted);
        if (entries.isEmpty()) {
            text.append(" (empty)");
        }
        for (FileMeta meta : entries) {
            text.append(INDENT).append(meta).append(" | stored on ")
                    .append(AppConfig.state().holdersFor(meta.getPath()).stream().map(ServantInfo::getKey).toList());
        }
        text.append("\nMy storage:");
        List<String> stored = AppConfig.storage().paths();
        if (stored.isEmpty()) {
            text.append(" (empty)");
        }
        for (String path : stored) {
            text.append(INDENT).append(path).append(" versions ").append(AppConfig.storage().versionsOf(path))
                    .append(AppConfig.storage().isTeamCopy(path) ? " (team copy)" : "");
        }
        text.append("\nMy work root:");
        List<String> tracked = AppConfig.workDir().trackedUnder("");
        if (tracked.isEmpty()) {
            text.append(" (nothing tracked)");
        }
        for (String path : tracked) {
            text.append(INDENT).append(path).append(" from v").append(AppConfig.workDir().baseVersion(path))
                    .append(AppConfig.workDir().exists(path) && AppConfig.workDir().isModified(path) ? " (modified)" : "");
        }
        return text.toString();
    }
}
