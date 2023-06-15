package servant.handler.mutex_messages;

import app.AppConfig;
import app.ServantInfo;
import mutex.DistributedMutex;
import mutex.SuzukiKasamiDistributedMutex;
import servant.handler.MessageHandler;
import servant.message.Message;
import servant.message.MessageType;
import servant.message.mutex_messages.TokenMessage;
import servant.message.util.MessageUtil;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TokenMessageHandler implements MessageHandler {
    private final Message clientMessage;
    private SuzukiKasamiDistributedMutex mutex;
    private static Set<Message> receivedBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<Message, Boolean>());

    public TokenMessageHandler(Message clientMessage, DistributedMutex mutex) {
        this.clientMessage = clientMessage;
        if(mutex instanceof SuzukiKasamiDistributedMutex){
            this.mutex = (SuzukiKasamiDistributedMutex) mutex;
        }
        else{
            AppConfig.timestampedErrorPrint("Mutex is not Suzuki Kasami.");
        }
    }

    @Override
    public void run() {
        try{
            this.handle();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handle() {
        ServantInfo senderInfo = (ServantInfo) clientMessage.getOriginalSenderInfo();
//        ServantInfo lastSenderInfo = clientMessage.getRoute().size() == 0 ?
//                (ServantInfo) clientMessage.getOriginalSenderInfo() :
//                clientMessage.getRoute().get(clientMessage.getRoute().size()-1);
//        String text = String.format("Got %s from %s broadcast by %s",
//                clientMessage.getMessageText(), lastSenderInfo, senderInfo);
//        AppConfig.timestampedStandardPrint(text);

        if (senderInfo.getId() == AppConfig.myServantInfo.getId()) {
            //We are the sender :o someone bounced this back to us. /ignore
            AppConfig.timestampedStandardPrint("Got own message back. No rebroadcast.");
        } else {
            //Try to put in the set. Thread safe add ftw.
            boolean didPut = receivedBroadcasts.add(clientMessage);

            if (didPut) {
                TokenMessage tokenMessage;

                // Redudantna provera
                if(clientMessage.getMessageType() != MessageType.TOKEN) {
                    AppConfig.timestampedStandardPrint("Expected type of message is TOKEN.");
                    return;
                }
                tokenMessage = (TokenMessage) clientMessage;
                // Da li je za nas namenjen token
                if(tokenMessage.getQueue().peek().getId() == AppConfig.myServantInfo.getId()) {
                    AppConfig.myServantInfo.setTokenOwner(true);
                    AppConfig.myServantInfo.setToken(tokenMessage);
                    AppConfig.timestampedStandardPrint("I received the TOKEN I requested.");
                    return;
                }

                AppConfig.timestampedStandardPrint("Rebroadcasting... " + receivedBroadcasts.size());
                for (ServantInfo neighbour : AppConfig.myServantInfo.getNeighbours()) {
                    //Same tokenMessage, different receiver, and add us to the route table.
                    MessageUtil.sendMessage(clientMessage.changeReceiver(neighbour.getId()).makeMeASender());
                }
            } else {
                //We already got this from somewhere else. /ignore
                AppConfig.timestampedStandardPrint("Already had this. No rebroadcast.");
            }
        }
    }
}
