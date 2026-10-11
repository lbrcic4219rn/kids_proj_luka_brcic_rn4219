package servant.message;

import app.ServantInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Getter
@RequiredArgsConstructor
public class Message implements Serializable {
    @Serial
    private static final long serialVersionUID = -9075856313609777945L;
    private static final int MAX_PRINTED_PAYLOAD = 100;

    private final String id;
    private final MessageType type;
    private final ServantInfo sender;
    private final ServantInfo receiver;
    private final ServantInfo lastHop;
    private final int hops;
    private final String replyTo;
    private final boolean direct;
    private final Serializable payload;

    public boolean isBroadcast() {
        return receiver == null;
    }

    public Message forwardedBy(ServantInfo hop) {
        return new Message(id, type, sender, receiver, hop, hops + 1, replyTo, direct, payload);
    }

    @SuppressWarnings("unchecked")
    public <T> T payload() {
        return (T) payload;
    }

    @Override
    public String toString() {
        String text = payload == null ? "" : String.valueOf(payload).replace('\n', ' ');
        if (text.length() > MAX_PRINTED_PAYLOAD) {
            text = text.substring(0, MAX_PRINTED_PAYLOAD) + "...";
        }
        return "[" + type + " | from: " + sender.getKey() + " | to: " + (receiver == null ? "ALL" : receiver.getKey())
                + " | hops: " + hops + (text.isEmpty() ? "" : " | " + text) + "]";
    }
}
