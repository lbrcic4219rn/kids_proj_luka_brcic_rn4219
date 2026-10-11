package servant.message;

import app.ServantInfo;
import file.FileIndex;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;

public final class Payloads {
    private Payloads() {
    }

    public record TokenRequest(ServantInfo node, int sequenceNumber) implements Serializable {
    }

    public record NodeRemoval(ServantInfo node, boolean graceful) implements Serializable {
    }

    public record Welcome(ArrayList<ServantInfo> members, HashSet<ServantInfo> removed, FileIndex index,
                          long epoch) implements Serializable {
    }

    public record StateSync(ArrayList<ServantInfo> members, HashSet<ServantInfo> removed,
                            FileIndex index) implements Serializable {
    }

    public record StateDigest(int membership, int index) implements Serializable {
    }

    public record FileData(String path, int version, String content, boolean propagate) implements Serializable {
        @Override
        public String toString() {
            return path + " v" + version + (content == null ? " (no content)" : " (" + content.length() + " chars)");
        }
    }

    public record FileRef(String path, int version) implements Serializable {
    }

    public record FileBundle(String path, TreeMap<Integer, String> versions) implements Serializable {
        @Override
        public String toString() {
            return path + " versions " + versions.keySet();
        }
    }
}
