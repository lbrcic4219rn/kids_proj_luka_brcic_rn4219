package servant.message.add_new_node_messages;

import app.Sender;
import app.ServantInfo;
import app.SystemState;
import servant.message.BasicMessage;
import servant.message.MessageType;

public class SystemNodeSaysWelcome extends BasicMessage {

    private SystemState currentSystemState;
    public SystemNodeSaysWelcome(MessageType type, Sender originalSenderInfo, ServantInfo receiverInfo, String messageText, SystemState currentSystemState) {
        super(type, originalSenderInfo, receiverInfo, messageText);
        this.currentSystemState = currentSystemState;
    }

    public SystemState getCurrentSystemState() {
        return currentSystemState;
    }
}
