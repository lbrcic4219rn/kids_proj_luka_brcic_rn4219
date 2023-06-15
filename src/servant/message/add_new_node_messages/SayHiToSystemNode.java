package servant.message.add_new_node_messages;

import app.ServantInfo;
import servant.message.BasicMessage;
import servant.message.MessageType;

import java.util.List;

public class SayHiToSystemNode extends BasicMessage {
    public SayHiToSystemNode(MessageType type, ServantInfo originalSenderInfo, ServantInfo receiverInfo, String messageText) {
        super(type, originalSenderInfo, receiverInfo, messageText);
    }
}
