package app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class SystemState {
    public static final int REPLICAS = 3;

    private final TreeSet<ServantInfo> members = new TreeSet<>();
    private final Set<ServantInfo> removed = new HashSet<>();

    public static List<ServantInfo> neighboursOf(List<ServantInfo> ring, ServantInfo node) {
        int size = ring.size();
        int index = ring.indexOf(node);
        if (index < 0) {
            return List.of();
        }
        Set<ServantInfo> result = new LinkedHashSet<>();
        for (int step = 1; step < size; step *= 2) {
            result.add(ring.get((index + step) % size));
            result.add(ring.get(((index - step) % size + size) % size));
        }
        result.remove(node);
        return new ArrayList<>(result);
    }

    public static ServantInfo nextHop(List<ServantInfo> ring, ServantInfo from, ServantInfo target, Set<ServantInfo> excluded) {
        int targetIndex = ring.indexOf(target);
        List<ServantInfo> neighbours = neighboursOf(ring, from);
        if (targetIndex < 0 || !ring.contains(from) || neighbours.contains(target)) {
            return excluded.contains(target) ? null : target;
        }
        ServantInfo best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (ServantInfo neighbour : neighbours) {
            if (excluded.contains(neighbour)) {
                continue;
            }
            int distance = ringDistance(ring.indexOf(neighbour), targetIndex, ring.size());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = neighbour;
            }
        }
        return best;
    }

    public static int ringDistance(int a, int b, int size) {
        int direct = Math.abs(a - b);
        return Math.min(direct, size - direct);
    }

    public static List<ServantInfo> holdersFor(String path, Collection<ServantInfo> nodes) {
        List<ServantInfo> byHash = new ArrayList<>(nodes);
        byHash.sort(Comparator.comparingLong(ServantInfo::placementHash).thenComparing(ServantInfo::getId));
        long pathHash = ServantInfo.hashOf(path);
        int start = 0;
        for (int i = 0; i < byHash.size(); i++) {
            if (byHash.get(i).placementHash() >= pathHash) {
                start = i;
                break;
            }
        }
        List<ServantInfo> holders = new ArrayList<>();
        for (int i = 0; i < Math.min(REPLICAS, byHash.size()); i++) {
            holders.add(byHash.get((start + i) % byHash.size()));
        }
        return holders;
    }

    public synchronized boolean addNode(ServantInfo node) {
        if (removed.contains(node)) {
            return false;
        }
        return members.add(node);
    }

    public synchronized boolean removeNode(ServantInfo node) {
        removed.add(node);
        return members.remove(node);
    }

    public synchronized ServantInfo olderIncarnationOf(ServantInfo node) {
        for (ServantInfo member : members) {
            if (member.getKey().equals(node.getKey()) && member.getIncarnation() < node.getIncarnation()) {
                return member;
            }
        }
        return null;
    }

    public synchronized List<ServantInfo> getMembers() {
        return new ArrayList<>(members);
    }

    public synchronized Set<ServantInfo> getRemoved() {
        return new HashSet<>(removed);
    }

    public synchronized boolean isMember(ServantInfo node) {
        return members.contains(node);
    }

    public synchronized boolean isRemoved(ServantInfo node) {
        return removed.contains(node);
    }

    public synchronized List<ServantInfo> getNeighbours() {
        return neighboursOf(new ArrayList<>(members), AppConfig.me());
    }

    public synchronized ServantInfo nextHop(ServantInfo target, Set<ServantInfo> excluded) {
        return nextHop(new ArrayList<>(members), AppConfig.me(), target, excluded);
    }

    public synchronized List<ServantInfo> holdersFor(String path) {
        return holdersFor(path, members);
    }

    public synchronized int digest() {
        return Objects.hash(members.hashCode(), removed.hashCode());
    }
}
