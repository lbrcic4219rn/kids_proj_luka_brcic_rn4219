package servant.message.broadcast_messages;

import app.AppConfig;
import app.ServantInfo;
import app.SystemState;
import servant.message.BasicMessage;
import servant.message.Message;
import servant.message.MessageType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UpdateSystem extends BasicMessage {
    private SystemState currentSystemState;
    public UpdateSystem(MessageType type, ServantInfo originalSenderInfo, ServantInfo receiverInfo, List<ServantInfo> routeList, String messageText, int messageId, SystemState currentSystemState) {
        super(type, originalSenderInfo, receiverInfo, routeList, messageText, messageId);
        this.currentSystemState = currentSystemState;
    }
    public UpdateSystem(MessageType type, ServantInfo originalSenderInfo, ServantInfo receiverInfo, String messageText, SystemState currentSystemState) {
        super(type, originalSenderInfo, receiverInfo, messageText);
        this.currentSystemState = currentSystemState;
    }

    public SystemState getCurrentSystemState() {
        return currentSystemState;
    }

    @Override
    public Message changeReceiver(Integer newReceiverId) {
        boolean isNeighbour = AppConfig.myServantInfo.getNeighbours().stream().anyMatch(servantInfo -> servantInfo.getId() == newReceiverId);
        if (isNeighbour) {
            ServantInfo newReceiverInfo = AppConfig.systemState.getInfoById(newReceiverId);
            return new UpdateSystem(this.getMessageType(), (ServantInfo) getOriginalSenderInfo(), newReceiverInfo, getRoute(), getMessageText(), getMessageId(), this.currentSystemState);
        } else {
            AppConfig.timestampedErrorPrint("Trying to make a message for " + newReceiverId + " who is not a neighbor.");
            return null;
        }
    }

    @Override
    public Message makeMeASender() {
        ServantInfo newRouteItem = AppConfig.myServantInfo;
        List<ServantInfo> newRouteList = new ArrayList<>(this.getRoute());
        newRouteList.add(newRouteItem);
        return new UpdateSystem(this.getMessageType(), (ServantInfo) getOriginalSenderInfo(), (ServantInfo) this.getReceiverInfo(),
                newRouteList, this.getMessageText(), getMessageId(), this.currentSystemState);

    }
}
