package servant.handler.broadcast_messages;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import app.AppConfig;
import servant.handler.BroadcastHandler;
import servant.message.Message;
import servant.message.broadcast_messages.UpdateSystem;
import servant.message.util.BroadcastUtil;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class UpdateSystemHandler implements BroadcastHandler {
    private static final Set<Message> receivedBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<>());
    @Getter
    private final UpdateSystem message;

    @Override
    public void run() {
        // Necemo zvati handle direktno odavde, nego cemo se obratiti BroadcastUtilSender da obradi za nas ovu primljenu broadcast poruku.
        // On ce zvati handle kad treba.
        try {
            BroadcastUtil.processReceivedBroadcastMessage(this.message, this);
        } catch (Exception e) {
            AppConfig.timestampedErrorPrint("Error while handling message " + message + ": " + e);
        }
    }

    @Override
    public void handle() {
        // Kada cvor primi ovu poruku treba da update-uje stanje sistema tj. AppConfig.systemState i AppConfig.myServantInfo.

        boolean isTokenOwner = AppConfig.myServantInfo.isTokenOwner();

        AppConfig.applySystemState(message.getCurrentSystemState());

        AppConfig.myServantInfo.setTokenOwner(isTokenOwner);
    }

    @Override
    public Set<Message> getReceivedBroadcasts() {
        return receivedBroadcasts;
    }
}
