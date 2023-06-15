package servant.message;

import app.Receiver;
import app.Sender;
import app.ServantInfo;

import java.io.Serializable;
import java.util.List;

public interface Message extends Serializable {
    Sender getOriginalSenderInfo();
    List<ServantInfo> getRoute();
    Receiver getReceiverInfo();
    MessageType getMessageType();
    String getMessageText();
    int getMessageId();
    Message makeMeASender();
    Message changeReceiver(Integer newReceiverId);
}
