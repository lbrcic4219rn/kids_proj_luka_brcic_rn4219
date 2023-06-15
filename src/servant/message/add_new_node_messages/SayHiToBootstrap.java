package servant.message.add_new_node_messages;

import app.Receiver;
import app.ServantInfo;
import servant.message.BasicMessage;
import servant.message.MessageType;

import java.io.Serial;
import java.util.List;

public class SayHiToBootstrap extends BasicMessage {
    @Serial
    private static final long serialVersionUID = 3899837286642127636L;
    public SayHiToBootstrap(MessageType type, ServantInfo originalSenderInfo, Receiver receiverInfo, String messageText) {
        super(type, originalSenderInfo, receiverInfo, messageText);
    }
}
