package servant.handler.add_new_node_messages;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import app.ServantInfo;
import app.SystemState;
import servant.handler.MessageHandler;
import servant.message.add_new_node_messages.SystemNodeSaysWelcome;

@RequiredArgsConstructor
public class SystemNodeSaysWelcomeHandler implements MessageHandler {
    private final SystemNodeSaysWelcome message;

    @Override
    public void run() {
        try {
            this.handle();
        } catch (Exception e) {
            AppConfig.timestampedErrorPrint("Error while handling message " + message + ": " + e);
        }
    }

    @Override
    public void handle() {
        // Ovde treba update-ovati node koji je primio ovu poruku, pomocu trenutnog stanja sistema iz poruke.
        // Node koji je primio ovu poruku je novi node koji je tek ukljucen u sistem.
        ServantInfo receiver = (ServantInfo) this.message.getReceiverInfo();
        if (receiver.getId() != AppConfig.myServantInfo.getId()) { // Samo provera. Moze biti uklonjeno kasnije.
            AppConfig.timestampedErrorPrint("This node received message but he is not intended to.");
            return;
        }
        SystemState currentSystemState = message.getCurrentSystemState();
        AppConfig.applySystemState(currentSystemState);
        AppConfig.timestampedStandardPrint("Great, I'm in the system now. Currently number of nodes in the system is " + currentSystemState.getAllNodeInfo().size());
    }
}
