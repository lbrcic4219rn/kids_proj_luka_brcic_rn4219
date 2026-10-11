package servant.message.mutex_messages;

import lombok.Getter;
import lombok.Setter;

import app.AppConfig;
import app.ServantInfo;
import servant.message.BasicMessage;
import servant.message.Message;
import servant.message.MessageType;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Request poruka za token. Poruka sadrži id čvora(servant-a) koji zahteva token i redni broj kritične sekcije u koju ulazi taj čvor: <ID, RBKS>
 * Ovo je broadcast poruka.
 */
public class AskForToken extends BasicMessage {
    @Serial
    private static final long serialVersionUID = 2084490973699262440L;
    // Boj trazenja kriticne sekcije
    @Getter
    @Setter
    private Integer sm;

    public AskForToken(ServantInfo sender, ServantInfo receiver, long timeStamp, Integer sm) {
        super(MessageType.ASK_FOR_TOKEN, sender, receiver, String.valueOf(timeStamp));
        this.sm = sm;
    }

    public AskForToken(ServantInfo originalSenderInfo, ServantInfo receiverInfo,
                       List<ServantInfo> routeList, String messageText, int messageId, Integer sm) {
        super(MessageType.ASK_FOR_TOKEN, originalSenderInfo, receiverInfo, routeList, messageText, messageId);
        this.sm = sm;
    }

    @Override
    public Message changeReceiver(Integer newReceiverId) {
        boolean isNeighbour = AppConfig.myServantInfo.getNeighbours().stream().anyMatch(servantInfo -> servantInfo.getId() == newReceiverId);
        if (isNeighbour) {
            ServantInfo newReceiverInfo = AppConfig.systemState.getInfoById(newReceiverId);
            return new AskForToken((ServantInfo) getOriginalSenderInfo(), newReceiverInfo, getRoute(), getMessageText(), getMessageId(), sm);
        } else {
            AppConfig.timestampedErrorPrint("Trying to make a message for " + newReceiverId + " who is not a neighbor.");
            return null;
        }

    }

    @Override
    public Message makeMeASender() {
        ServantInfo newRouteItem = AppConfig.myServantInfo;

        List<ServantInfo> newRouteList = new ArrayList<>(getRoute());
        newRouteList.add(newRouteItem);
        return new AskForToken((ServantInfo) getOriginalSenderInfo(),
                (ServantInfo) getReceiverInfo(), newRouteList, getMessageText(), getMessageId(), sm);
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
