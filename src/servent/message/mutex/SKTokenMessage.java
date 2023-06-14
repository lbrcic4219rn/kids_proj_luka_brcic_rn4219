package servent.message.mutex;

import app.AppConfig;
import app.ServentInfo;
import servent.message.BasicMessage;
import servent.message.Message;
import servent.message.MessageType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SKTokenMessage extends BasicMessage {
    private static final long serialVersionUID = 2084490973699262440L;
    private Map<ServentInfo, Integer> lnMap = new ConcurrentHashMap<>();
    private Queue<ServentInfo> queue = new LinkedList<>();

    public SKTokenMessage(ServentInfo originalSenderInfo, ServentInfo receiverInfo, Map<ServentInfo, Integer> lnMap, Queue<ServentInfo> queue) {
        super(MessageType.SUZUKI_KASAMI_TOKEN, originalSenderInfo, receiverInfo);
        this.queue = queue;
        this.lnMap = lnMap;
    }

    public SKTokenMessage(ServentInfo originalSenderInfo, ServentInfo receiverInfo,
                          List<ServentInfo> routeList, String messageText, int messageId, Map<ServentInfo, Integer> lnMap, Queue<ServentInfo> queue) {
        super(MessageType.SUZUKI_KASAMI_TOKEN, originalSenderInfo, receiverInfo, routeList, messageText, messageId);
        this.queue = queue;
        this.lnMap = lnMap;
    }

    public Map<ServentInfo, Integer> getLnMap() {
        return lnMap;
    }

    public void setLnMap(Map<ServentInfo, Integer> lnMap) {
        this.lnMap = lnMap;
    }

    public Queue<ServentInfo> getQueue() {
        return queue;
    }

    public void setQueue(Queue<ServentInfo> queue) {
        this.queue = queue;
    }

    @Override
    public Message changeReceiver(Integer newReceiverId) {
        if (AppConfig.myServentInfo.getNeighbors().contains(newReceiverId)) {
            ServentInfo newReceiverInfo = AppConfig.getInfoById(newReceiverId);

            Message toReturn = new SKTokenMessage(getOriginalSenderInfo(),
                    newReceiverInfo, getRoute(), getMessageText(), getMessageId(), lnMap, queue);

            return toReturn;
        } else {
            AppConfig.timestampedErrorPrint("Trying to make a message for " + newReceiverId + " who is not a neighbor.");

            return null;
        }

    }

    @Override
    public Message makeMeASender() {
        ServentInfo newRouteItem = AppConfig.myServentInfo;

        List<ServentInfo> newRouteList = new ArrayList<>(getRoute());
        newRouteList.add(newRouteItem);
        Message toReturn = new SKTokenMessage(getOriginalSenderInfo(),
                getReceiverInfo(), newRouteList, getMessageText(), getMessageId(), lnMap, queue);

        return toReturn;
    }
}
