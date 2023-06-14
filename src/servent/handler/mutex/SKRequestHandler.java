package servent.handler.mutex;

import app.AppConfig;
import app.ServentInfo;
import mutex.DistributedMutex;
import mutex.SuzukiKasamiMutex;
import servent.handler.MessageHandler;
import servent.message.BasicMessage;
import servent.message.Message;
import servent.message.MessageType;
import servent.message.mutex.SKRequestMessage;
import servent.message.mutex.SKTokenMessage;
import servent.message.util.MessageUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SKRequestHandler implements MessageHandler {

    private final Message clientMessage;
    private SuzukiKasamiMutex mutex;
    private static Set<Message> receivedBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<Message, Boolean>());

    public SKRequestHandler(Message clientMessage, DistributedMutex mutex) {
        this.clientMessage = clientMessage;
        if(mutex instanceof SuzukiKasamiMutex){
            this.mutex = (SuzukiKasamiMutex) mutex;
        }
        else{
            AppConfig.timestampedErrorPrint("mutex nije Lamport");
        }
    }

    //SVOJ NIZ TREBA DA UPDEJTUJE RN
    @Override
    public void run() {

        long messageTimeStamp = Long.parseLong(clientMessage.getMessageText());
        mutex.updateTimeStamp(messageTimeStamp);

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
            AppConfig.timestampedStandardPrint("nije nasa poruka");
            if (didPut) {
                Map<ServentInfo, Integer> rn =  AppConfig.myServentInfo.getRnMap();
                SKRequestMessage message;
                AppConfig.timestampedStandardPrint("ubacili");

                // Redudantna provera
                if(clientMessage.getMessageType() != MessageType.SUZUKI_KASAMI_REQUEST) {
                    AppConfig.timestampedStandardPrint("Ocekivan ti poruke je suzuki-kasami request");
                    return;
                }
                AppConfig.timestampedStandardPrint("prosao redudantan");
                System.out.println("Client message type: " + clientMessage.getClass().getSimpleName());

                message = (SKRequestMessage) clientMessage;
                AppConfig.timestampedStandardPrint(senderInfo.toString());

                if(rn.get(senderInfo) == null)
                    rn.put(senderInfo, 0);
                AppConfig.timestampedStandardPrint("prosao postavljanje rn");

                if(message.getSm() > rn.get(senderInfo)) {
                    AppConfig.myServentInfo.getRnMap().put(senderInfo, message.getSm());
                } else {
                    AppConfig.timestampedStandardPrint("Zakasnela poruka");
                    return;
                }

                // Mozemo garantovati da ce svaki cvor dobiti ovu poruku kako svaki cvor ima 4 suseda
                System.out.println(this.mutex.getDistributedMutexInitiated().get());
                if(AppConfig.myServentInfo.isHasToken() && this.mutex.getDistributedMutexInitiated().get()) {
                    System.out.println("ne dam, moj token");
                    //stavi u queue
                    AppConfig.myServentInfo.getToken().getQueue().add(senderInfo);
                    System.out.println("na cekanju si");
                } else {
                    System.out.println("ne triba mi");
                    SKTokenMessage tokenMessage = new SKTokenMessage(
                            AppConfig.myServentInfo,
                            null,
                            AppConfig.myServentInfo.getToken().getLnMap(),
                            AppConfig.myServentInfo.getToken().getQueue());
                    for (Integer neighbor : AppConfig.myServentInfo.getNeighbors()) {
                        //Same message, different receiver, and add us to the route table.
                        MessageUtil.sendMessage(tokenMessage.changeReceiver(neighbor).makeMeASender());
                    }
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
