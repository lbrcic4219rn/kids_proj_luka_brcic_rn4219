package servant.handler.broadcast_messages;

import app.AppConfig;
import app.SystemState;
import servant.handler.BroadcastHandler;
import servant.message.Message;
import servant.message.broadcast_messages.UpdateSystem;
import servant.message.util.BroadcastUtil;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UpdateSystemHandler implements BroadcastHandler {
    private UpdateSystem message;
    private static Set<Message> receivedBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<Message, Boolean>());

    public UpdateSystemHandler(UpdateSystem message) {
        this.message = message;
    }

    @Override
    public void run() {
        // Necemo zvati handle direktno odavde, nego cemo se obratiti BroadcastUtilSender da obradi za nas ovu primljenu broadcast poruku.
        // On ce zvati handle kad treba.
        try {
            BroadcastUtil.processReceivedBroadcastMessage(this.message, this);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public UpdateSystem getMessage() {
        return message;
    }

    @Override
    public void handle() {
        // Kada cvor primi ovu poruku treba da update-uje stanje sistema tj. AppConfig.systemState i AppConfig.myServantInfo.

        boolean isTokenOwner = AppConfig.myServantInfo.isTokenOwner();

        SystemState currentSystemState = message.getCurrentSystemState();
        AppConfig.systemState = currentSystemState;
        AppConfig.myServantInfo = currentSystemState.getInfoById(AppConfig.myServantInfo.getId());

        AppConfig.myServantInfo.setTokenOwner(isTokenOwner);
    }

    @Override
    public Set<Message> getReceivedBroadcasts() {
        return receivedBroadcasts;
    }
}
