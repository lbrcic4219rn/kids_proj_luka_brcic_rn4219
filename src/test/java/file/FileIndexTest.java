package file;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileIndexTest {
    private static FileMeta meta(String path, int version, long stamp) {
        FileMeta meta = new FileMeta(path);
        meta.setVersion(version);
        meta.setStamp(stamp);
        return meta;
    }

    @Test
    void newerStampWinsAndOlderIsIgnored() {
        FileIndex index = new FileIndex();
        assertTrue(index.apply(meta("a.txt", 0, 1)));
        assertTrue(index.apply(meta("a.txt", 1, 2)));
        assertFalse(index.apply(meta("a.txt", 0, 1)));
        assertEquals(1, index.get("a.txt").getVersion());
    }

    @Test
    void mergeConvergesRegardlessOfDirection() {
        FileIndex first = new FileIndex();
        FileIndex second = new FileIndex();
        first.apply(meta("a.txt", 2, 5));
        first.apply(meta("b.txt", 0, 1));
        second.apply(meta("a.txt", 1, 3));
        second.apply(meta("c.txt", 0, 4));
        assertNotEquals(first.digest(), second.digest());

        first.merge(second);
        second.merge(first);

        assertEquals(first.digest(), second.digest());
        assertEquals(2, second.get("a.txt").getVersion());
        assertEquals(List.of("a.txt", "b.txt", "c.txt"), first.livePathsUnder(""));
    }

    @Test
    void directoryNameMatchesEverythingBelowItAndSkipsRemovedFiles() {
        FileIndex index = new FileIndex();
        index.apply(meta("docs/a.txt", 0, 1));
        index.apply(meta("docs/sub/b.txt", 0, 2));
        index.apply(meta("docs2/c.txt", 0, 3));
        FileMeta removed = meta("docs/old.txt", 0, 4);
        removed.setDeleted(true);
        index.apply(removed);

        assertEquals(List.of("docs/a.txt", "docs/sub/b.txt"), index.livePathsUnder("docs"));
        assertEquals(List.of("docs/a.txt"), index.livePathsUnder("docs/a.txt"));
        assertTrue(index.livePathsUnder("missing").isEmpty());
    }

    @Test
    void returnedEntriesAreCopies() {
        FileIndex index = new FileIndex();
        index.apply(meta("a.txt", 0, 1));
        index.get("a.txt").setVersion(7);
        assertEquals(0, index.get("a.txt").getVersion());
    }
}
