package servent.handler.mutex;

import app.AppConfig;
import app.ServentInfo;
import mutex.DistributedMutex;
import mutex.SuzukiKasamiMutex;
import servent.handler.MessageHandler;
import servent.message.Message;
import servent.message.MessageType;
import servent.message.mutex.SKRequestMessage;
import servent.message.mutex.SKTokenMessage;
import servent.message.util.MessageUtil;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SKTokenHandler implements MessageHandler {

    private final Message clientMessage;
    private SuzukiKasamiMutex mutex;
    private static Set<Message> receivedBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<Message, Boolean>());

    public SKTokenHandler(Message clientMessage, DistributedMutex mutex) {
        this.clientMessage = clientMessage;
        if(mutex instanceof SuzukiKasamiMutex){
            this.mutex = (SuzukiKasamiMutex) mutex;
        }
        else{
            AppConfig.timestampedErrorPrint("mutex nije Suzuki kasami");
        }
    }

    @Override
    public void run() {
//        System.out.println("aloooooo");
//        long messageTimeStamp = Long.parseLong(clientMessage.getMessageText());
//        mutex.updateTimeStamp(messageTimeStamp);
        System.out.println("aloooooo");
        ServentInfo senderInfo = clientMessage.getOriginalSenderInfo();
        ServentInfo lastSenderInfo = clientMessage.getRoute().size() == 0 ?
                clientMessage.getOriginalSenderInfo() :
                clientMessage.getRoute().get(clientMessage.getRoute().size()-1);
        String text = String.format("Got %s from %s broadcast by %s",
                clientMessage.getMessageText(), lastSenderInfo, senderInfo);

        AppConfig.timestampedStandardPrint(text);

        if (senderInfo.getId() == AppConfig.myServentInfo.getId()) {
            //We are the sender :o someone bounced this back to us. /ignore
            AppConfig.timestampedStandardPrint("Got own message back. No rebroadcast.");
        } else {
            //Try to put in the set. Thread safe add ftw.
            boolean didPut = receivedBroadcasts.add(clientMessage);

            if (didPut) {
                SKTokenMessage message;

                // Redudantna provera
                if(clientMessage.getMessageType() != MessageType.SUZUKI_KASAMI_TOKEN) {
                    AppConfig.timestampedStandardPrint("Ocekivan ti poruke je suzuki-kasami request");
                    return;
                }
                System.out.println("cek cek");
                System.out.println("Client message type: " + clientMessage.getClass().getSimpleName());
                message = (SKTokenMessage) clientMessage;
                System.out.println("cek cek");
                // Da li je za nas namenjen token
                if(message.getQueue().peek().getId() == AppConfig.myServentInfo.getId()) {
                    AppConfig.myServentInfo.setHasToken(true);
                    AppConfig.myServentInfo.setToken(message);
                    return;
                }

                AppConfig.timestampedStandardPrint("Rebroadcasting... " + receivedBroadcasts.size());
                for (Integer neighbor : AppConfig.myServentInfo.getNeighbors()) {
                    //Same message, different receiver, and add us to the route table.
                    MessageUtil.sendMessage(clientMessage.changeReceiver(neighbor).makeMeASender());
                }

            } else {
                //We already got this from somewhere else. /ignore
                AppConfig.timestampedStandardPrint("Already had this. No rebroadcast.");
            }
        }
    }
}
