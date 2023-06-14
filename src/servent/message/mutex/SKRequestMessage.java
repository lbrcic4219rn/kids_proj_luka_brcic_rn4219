package servent.message.mutex;

import app.AppConfig;
import app.ServentInfo;
import servent.message.BasicMessage;
import servent.message.Message;
import servent.message.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class SKRequestMessage extends BasicMessage {

    private static final long serialVersionUID = 2084490973699262440L;
    // Boj trazenja kriticne sekcije
    private Integer sm;
    public SKRequestMessage(ServentInfo sender, ServentInfo receiver, long timeStamp, Integer sm) {
        super(MessageType.SUZUKI_KASAMI_REQUEST, sender, receiver, String.valueOf(timeStamp));
        this.sm = sm;
    }

    public SKRequestMessage(ServentInfo originalSenderInfo, ServentInfo receiverInfo,
                          List<ServentInfo> routeList, String messageText, int messageId, Integer sm) {
        super(MessageType.SUZUKI_KASAMI_REQUEST, originalSenderInfo, receiverInfo, routeList, messageText, messageId);
        this.sm = sm;
    }

    public Integer getSm() {
        return sm;
    }

    public void setSm(Integer sm) {
        this.sm = sm;
    }

    @Override
    public Message changeReceiver(Integer newReceiverId) {
        if (AppConfig.myServentInfo.getNeighbors().contains(newReceiverId)) {
            ServentInfo newReceiverInfo = AppConfig.getInfoById(newReceiverId);

            Message toReturn = new SKRequestMessage(getOriginalSenderInfo(),
                    newReceiverInfo, getRoute(), getMessageText(), getMessageId(), sm);

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
        Message toReturn = new SKRequestMessage(getOriginalSenderInfo(),
                getReceiverInfo(), newRouteList, getMessageText(), getMessageId(), sm);

        return toReturn;
    }
}
