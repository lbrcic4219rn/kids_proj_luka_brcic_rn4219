package mutex;

import app.ServantInfo;
import file.FileIndex;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@Getter
public class Token implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, Integer> ln = new HashMap<>();
    private final Deque<ServantInfo> queue = new ArrayDeque<>();
    private final FileIndex index;
    @Setter
    private long epoch;
    private long stamp;

    public Token(long epoch, long stamp, FileIndex index) {
        this.epoch = epoch;
        this.stamp = stamp;
        this.index = index;
    }

    public long nextStamp() {
        return ++stamp;
    }
}
