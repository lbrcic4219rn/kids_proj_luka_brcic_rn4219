package servant.handler;

import servant.message.Message;

import java.util.Set;

public interface BroadcastHandler extends MessageHandler {
    Set<Message> getReceivedBroadcasts();
}
