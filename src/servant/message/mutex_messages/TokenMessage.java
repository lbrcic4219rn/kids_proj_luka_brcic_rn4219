package servant.message.mutex_messages;

import lombok.Getter;
import lombok.Setter;

import app.AppConfig;
import app.ServantInfo;
import servant.message.BasicMessage;
import servant.message.Message;
import servant.message.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * U tokenu se šalje queue(red čekanja svih zahteva za token - za sve čvorove) i mapu gde postoji entry za svaki čvor:
 * ključ je id čvora, value je redni broj kritične sekcije u koju taj čvor ulazi <ID, RBKS>
 * Može da se šalje kao broadcast poruka - na taj način ćemo biti sigurno da je token stigao do onog čvora koji ga je zahtevao.
 * Može da se šalje ne kao broadcast poruka, već da čvor koji šalje token nekim algoritmom izračuna putanju kojom će slati
 * token do čvora koji ga je zahtevao.
 *
 */
@Getter
@Setter
public class TokenMessage extends BasicMessage {
    private Map<ServantInfo, Integer> lnMap;
    private Queue<ServantInfo> queue;

    public TokenMessage(ServantInfo originalSenderInfo, ServantInfo receiverInfo, Map<ServantInfo, Integer> lnMap, Queue<ServantInfo> queue) {
        super(MessageType.TOKEN, originalSenderInfo, receiverInfo);
        this.queue = queue;
        this.lnMap = lnMap;
    }

    public TokenMessage(ServantInfo originalSenderInfo, ServantInfo receiverInfo,
                        List<ServantInfo> routeList, String messageText, int messageId, Map<ServantInfo, Integer> lnMap, Queue<ServantInfo> queue) {
        super(MessageType.TOKEN, originalSenderInfo, receiverInfo, routeList, messageText, messageId);
        this.queue = queue;
        this.lnMap = lnMap;
    }

    @Override
    public Message changeReceiver(Integer newReceiverId) {
        boolean isNeighbour = AppConfig.myServantInfo.getNeighbours().stream().anyMatch(servantInfo -> servantInfo.getId() == newReceiverId);
        if (isNeighbour) {
            ServantInfo newReceiverInfo = AppConfig.systemState.getInfoById(newReceiverId);

            return new TokenMessage((ServantInfo) getOriginalSenderInfo(),
                    newReceiverInfo, getRoute(), getMessageText(), getMessageId(), lnMap, queue);
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
        return new TokenMessage((ServantInfo) getOriginalSenderInfo(),
                (ServantInfo) getReceiverInfo(), newRouteList, getMessageText(), getMessageId(), lnMap, queue);
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
