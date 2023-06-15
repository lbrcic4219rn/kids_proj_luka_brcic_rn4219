package servant.message.broadcast_messages;

import app.ServantInfo;
import servant.message.BasicMessage;
import servant.message.MessageType;

import java.util.List;

public class SystemIsUpdated extends BasicMessage {
    public SystemIsUpdated(MessageType type, ServantInfo originalSenderInfo, ServantInfo receiverInfo, List<ServantInfo> routeList, String messageText, int messageId) {
        super(type, originalSenderInfo, receiverInfo, routeList, messageText, messageId);
    }
}
