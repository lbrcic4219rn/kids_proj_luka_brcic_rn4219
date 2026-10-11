package app;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemStateTest {
    private static List<ServantInfo> ring(int size) {
        List<ServantInfo> nodes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            nodes.add(new ServantInfo("localhost", 1100 + i, i % 2 == 0 ? "a" : "b", 1));
        }
        Collections.sort(nodes);
        return nodes;
    }

    private static int log2Ceil(int value) {
        return 32 - Integer.numberOfLeadingZeros(Math.max(1, value - 1));
    }

    @Test
    void smallSystemIsFullyConnected() {
        List<ServantInfo> ring = ring(5);
        for (ServantInfo node : ring) {
            assertEquals(4, SystemState.neighboursOf(ring, node).size());
        }
    }

    @Test
    void neighbourCountIsLogarithmicAndLinksAreSymmetric() {
        for (int size : new int[]{2, 8, 33, 100}) {
            List<ServantInfo> ring = ring(size);
            for (ServantInfo node : ring) {
                List<ServantInfo> neighbours = SystemState.neighboursOf(ring, node);
                assertTrue(neighbours.size() <= 2 * log2Ceil(size), "too many neighbours for size " + size);
                assertFalse(neighbours.contains(node));
                for (ServantInfo neighbour : neighbours) {
                    assertTrue(SystemState.neighboursOf(ring, neighbour).contains(node));
                }
            }
        }
    }

    @Test
    void anyNodeIsReachableInLogarithmicNumberOfHops() {
        for (int size : new int[]{6, 17, 64, 100}) {
            List<ServantInfo> ring = ring(size);
            for (ServantInfo from : ring) {
                for (ServantInfo target : ring) {
                    if (from.equals(target)) {
                        continue;
                    }
                    int hops = 0;
                    ServantInfo current = from;
                    while (!current.equals(target)) {
                        current = SystemState.nextHop(ring, current, target, Set.of());
                        assertNotNull(current);
                        hops++;
                        assertTrue(hops <= log2Ceil(size) + 1, "too many hops for size " + size);
                    }
                }
            }
        }
    }

    @Test
    void routingAvoidsUnreachableNeighbour() {
        List<ServantInfo> ring = ring(16);
        ServantInfo from = ring.get(0);
        ServantInfo target = ring.get(7);
        ServantInfo first = SystemState.nextHop(ring, from, target, Set.of());
        assertNotNull(first);
        ServantInfo second = SystemState.nextHop(ring, from, target, Set.of(first));
        assertNotNull(second);
        assertNotEquals(first, second);
    }

    @Test
    void nodesThatAreNotMembersAreContactedDirectly() {
        List<ServantInfo> ring = ring(16);
        ServantInfo outsider = new ServantInfo("localhost", 1000, "", 0);
        assertEquals(outsider, SystemState.nextHop(ring, ring.get(0), outsider, Set.of()));
    }

    @Test
    void membersOfTheSameTeamAreNextToEachOtherInTheRing() {
        List<ServantInfo> ring = ring(10);
        for (int i = 0; i < 5; i++) {
            assertEquals("a", ring.get(i).getTeam());
            assertEquals("b", ring.get(i + 5).getTeam());
        }
    }

    @Test
    void everyFileHasThreeHoldersAndLosingANodeMovesOnlyItsFiles() {
        List<ServantInfo> ring = ring(10);
        ServantInfo failed = ring.get(3);
        List<ServantInfo> survivors = new ArrayList<>(ring);
        survivors.remove(failed);
        Set<ServantInfo> used = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String path = "dir/file" + i + ".txt";
            List<ServantInfo> holders = SystemState.holdersFor(path, ring);
            assertEquals(SystemState.REPLICAS, new HashSet<>(holders).size());
            used.addAll(holders);
            List<ServantInfo> after = SystemState.holdersFor(path, survivors);
            if (holders.contains(failed)) {
                List<ServantInfo> kept = new ArrayList<>(holders);
                kept.remove(failed);
                assertTrue(after.containsAll(kept));
            } else {
                assertEquals(holders, after);
            }
        }
        assertEquals(10, used.size(), "files should be spread over all nodes");
    }

    @Test
    void removedNodeCannotBeAddedAgainButRestartedNodeCan() {
        SystemState state = new SystemState();
        ServantInfo node = new ServantInfo("localhost", 1100, "a", 1);
        assertTrue(state.addNode(node));
        assertTrue(state.removeNode(node));
        assertFalse(state.addNode(node));
        assertTrue(state.addNode(new ServantInfo("localhost", 1100, "a", 2)));
    }
}
