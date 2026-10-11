package servant.message.add_new_node_messages;

import lombok.Getter;

import app.Receiver;
import app.Sender;
import servant.message.BasicMessage;
import servant.message.MessageType;

public class BootstrapSaysHello extends BasicMessage {
    @Getter
    private final int portOfSystemNodeToAsk;

    public BootstrapSaysHello(MessageType type, Sender originalSenderInfo, Receiver receiverInfo, String messageText, int portOfSystemNodeToAsk) {
        super(type, originalSenderInfo, receiverInfo, messageText);
        this.portOfSystemNodeToAsk = portOfSystemNodeToAsk;
    }

    // Jednakost poruka je namerno ista kao u BasicMessage (id poruke + id posiljaoca) da bi radila detekcija vec primljenih broadcast poruka.
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
