package servant.message.add_new_node_messages;

import lombok.Getter;

import app.Sender;
import app.ServantInfo;
import app.SystemState;
import servant.message.BasicMessage;
import servant.message.MessageType;

public class SystemNodeSaysWelcome extends BasicMessage {

    @Getter
    private final SystemState currentSystemState;

    public SystemNodeSaysWelcome(MessageType type, Sender originalSenderInfo, ServantInfo receiverInfo, String messageText, SystemState currentSystemState) {
        super(type, originalSenderInfo, receiverInfo, messageText);
        this.currentSystemState = currentSystemState;
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
