package servant.handler.add_new_node_messages;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import app.ServantInfo;
import mutex.DistributedMutex;
import servant.handler.MessageHandler;
import servant.message.Message;
import servant.message.MessageType;
import servant.message.add_new_node_messages.SayHiToSystemNode;
import servant.message.add_new_node_messages.SystemNodeSaysWelcome;
import servant.message.broadcast_messages.UpdateSystem;
import servant.message.util.BroadcastUtil;
import servant.message.util.MessageUtil;

@RequiredArgsConstructor
public class SayHiToSystemNodeHandler implements MessageHandler {
    private final SayHiToSystemNode message;
    private final DistributedMutex mutex;

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
        ServantInfo newNode = (ServantInfo) message.getOriginalSenderInfo();

        this.mutex.lock();

        AppConfig.systemState.addNewNodeInSystem(newNode);
        AppConfig.refreshMyServantInfo();

        Message welcome = new SystemNodeSaysWelcome(MessageType.SYSTEM_NODE_SAYS_WELCOME, AppConfig.myServantInfo,
                newNode, "Welcome new node with id " + newNode.getId(), AppConfig.systemState);
        MessageUtil.sendMessage(welcome);

        UpdateSystem updateSystem = new UpdateSystem(MessageType.NEW_NODE_ARRIVAL, AppConfig.myServantInfo,
                null, "New node has arrived, you should update system state.", AppConfig.systemState);

        // Ne zelimo da novi node primi ovu poruku da treba da update-uje sistem jer nema smisla.
        // On je taj zbog koga drugi update-uju sistem, on vec ima najnovije informacije.
        // Zbog toga zovemo ovu metodu gde saljemno svima osim njemu.
        BroadcastUtil.sendBroadcastMessageAndExcludeNodeWithId(AppConfig.myServantInfo.getNeighbours(), updateSystem, newNode.getId());

        this.mutex.unlock();
    }
}
