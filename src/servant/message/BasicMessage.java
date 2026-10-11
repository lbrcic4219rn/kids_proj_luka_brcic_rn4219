package servant.message;

import lombok.Getter;

import app.*;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class BasicMessage implements Message {
    @Serial
    private static final long serialVersionUID = -9075856313609777945L;
    private static final AtomicInteger messageCounter = new AtomicInteger(0);
    private final MessageType type;
    @Getter
    private final Sender originalSenderInfo;
    @Getter
    private final Receiver receiverInfo; // receiver is always instance of ServantInfo except when adding new node -> in that case: BootstrapServer
    private final List<ServantInfo> routeList;
    @Getter
    private final String messageText;
    @Getter
    private final int messageId;

    public BasicMessage(MessageType type, Sender originalSenderInfo, Receiver receiverInfo) {
        this.type = type;
        this.originalSenderInfo = originalSenderInfo;
        this.receiverInfo = receiverInfo;
        this.routeList = new ArrayList<>();
        this.messageText = "";
        this.messageId = messageCounter.getAndIncrement();
    }

    public BasicMessage(MessageType type, Sender originalSenderInfo, Receiver receiverInfo, String messageText) {
        this.type = type;
        this.originalSenderInfo = originalSenderInfo;
        this.receiverInfo = receiverInfo;
        this.routeList = new ArrayList<>();
        this.messageText = messageText;
        this.messageId = messageCounter.getAndIncrement();
    }

    protected BasicMessage(MessageType type, Sender originalSenderInfo, Receiver receiverInfo, List<ServantInfo> routeList, String messageText, int messageId) {
        this.type = type;
        this.originalSenderInfo = originalSenderInfo;
        this.receiverInfo = receiverInfo;
        this.routeList = routeList;
        this.messageText = messageText;
        this.messageId = messageId;
    }

    @Override
    public MessageType getMessageType() {
        return type;
    }

    @Override
    public List<ServantInfo> getRoute() {
        return routeList;
    }

    @Override
    public Message makeMeASender() {
        ServantInfo newRouteItem = AppConfig.myServantInfo;
        List<ServantInfo> newRouteList = new ArrayList<>(routeList);
        newRouteList.add(newRouteItem);
        return new BasicMessage(getMessageType(), getOriginalSenderInfo(), getReceiverInfo(), newRouteList, getMessageText(), getMessageId());
    }

    @Override
    public Message changeReceiver(Integer newReceiverId) {
        boolean isNeighbour = AppConfig.myServantInfo.getNeighbours().stream().anyMatch(servantInfo -> servantInfo.getId() == newReceiverId);
        if (isNeighbour) {
            ServantInfo newReceiverInfo = AppConfig.systemState.getInfoById(newReceiverId);
            return new BasicMessage(getMessageType(), getOriginalSenderInfo(), newReceiverInfo, getRoute(), getMessageText(), getMessageId());
        } else {
            AppConfig.timestampedErrorPrint("Trying to make a message for " + newReceiverId + " who is not a neighbor.");
            return null;
        }

    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BasicMessage other) {
            if (this.getOriginalSenderInfo() instanceof BootstrapServer || other.getOriginalSenderInfo() instanceof BootstrapServer) {
                return false;
            }
            ServantInfo senderInfo = (ServantInfo) this.getOriginalSenderInfo();
            ServantInfo otherSenderInfo = (ServantInfo) other.getOriginalSenderInfo();
            return getMessageId() == other.getMessageId() &&
                    senderInfo.getId() == otherSenderInfo.getId();
        }

        return false;
    }

    @Override
    public int hashCode() {
        if (getOriginalSenderInfo() instanceof BootstrapServer) {
            return -5; //ovo je random
        }
        Integer id = ((ServantInfo) getOriginalSenderInfo()).getId();
        return Objects.hash(getMessageId(), id);
    }

    @Override
    public String toString() {
        String senderId = this.getSenderIdFromMessageAsString();
        String receiverId = this.getReceiverIdFromMessageAsString();

        return "[ senderId: " + senderId + " | messageId: " + getMessageId() + " | messageText: " +
                getMessageText() + " | messageType: " + getMessageType() + " | receiverId: " +
                receiverId + "]";
    }

    private String getSenderIdFromMessageAsString() {
        Sender sender = getOriginalSenderInfo();
        if (sender instanceof BootstrapServer) {
            return "[BootstrapServer ID]";
        }
        int id = ((ServantInfo) sender).getId();

        return String.valueOf(id);
    }

    private String getReceiverIdFromMessageAsString() {
        Receiver receiver = getReceiverInfo();
        if (receiver == null) {
            return null;
        }
        if (receiver instanceof BootstrapServer) {
            return "[BootstrapServer ID]";
        }
        int id = ((ServantInfo) receiver).getId();
        return String.valueOf(id);
    }
}
