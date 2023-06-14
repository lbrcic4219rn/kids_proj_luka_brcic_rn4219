package servent.message;

import app.ServentInfo;

public class BroadcastMessage extends BasicMessage {

    private static final long serialVersionUID = 1574649068760262710L;

    public BroadcastMessage(ServentInfo senderInfo, ServentInfo receiverInfo, String messageText) {
        super(MessageType.BROADCAST, senderInfo, receiverInfo, messageText);
    }

}
