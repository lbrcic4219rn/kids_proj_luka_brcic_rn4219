package servant.message.add_new_node_messages;

import app.Receiver;
import app.Sender;
import servant.message.BasicMessage;
import servant.message.MessageType;

public class BootstrapSaysHello extends BasicMessage {
    private int portOfSystemNodeToAsk;

    public BootstrapSaysHello(MessageType type, Sender originalSenderInfo, Receiver receiverInfo, String messageText, int portOfSystemNodeToAsk) {
        super(type, originalSenderInfo, receiverInfo, messageText);
        this.portOfSystemNodeToAsk = portOfSystemNodeToAsk;
    }

    public int getPortOfSystemNodeToAsk() {
        return portOfSystemNodeToAsk;
    }
}
