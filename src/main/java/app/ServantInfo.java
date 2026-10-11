package app;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(of = {"ipAddress", "port", "incarnation"})
public class ServantInfo implements Serializable, Comparable<ServantInfo> {
    @Serial
    private static final long serialVersionUID = 5304170042791281555L;
    private static final Comparator<ServantInfo> RING_ORDER = Comparator.comparing(ServantInfo::getTeam)
            .thenComparing(ServantInfo::getIpAddress)
            .thenComparingInt(ServantInfo::getPort)
            .thenComparingLong(ServantInfo::getIncarnation);

    private final String ipAddress;
    private final int port;
    private final String team;
    private final long incarnation;

    public static long hashOf(String value) {
        int h = value.hashCode();
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h & 0xffffffffL;
    }

    public String getKey() {
        return ipAddress + ":" + port;
    }

    public String getId() {
        return getKey() + "#" + incarnation;
    }

    public long placementHash() {
        return hashOf(getKey());
    }

    @Override
    public int compareTo(ServantInfo other) {
        return RING_ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return team.isEmpty() ? getKey() : getKey() + "(" + team + ")";
    }
}
