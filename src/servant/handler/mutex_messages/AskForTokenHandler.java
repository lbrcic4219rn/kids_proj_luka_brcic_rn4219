package servant.handler.mutex_messages;

import app.AppConfig;
import app.ServantInfo;
import mutex.DistributedMutex;
import mutex.SuzukiKasamiDistributedMutex;
import servant.handler.MessageHandler;
import servant.message.Message;
import servant.message.MessageType;
import servant.message.mutex_messages.AskForToken;
import servant.message.mutex_messages.TokenMessage;
import servant.message.util.MessageUtil;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AskForTokenHandler implements MessageHandler {
    private final Message clientMessage;
    private SuzukiKasamiDistributedMutex mutex;
    private static Set<Message> receivedBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<Message, Boolean>());

    public AskForTokenHandler(Message clientMessage, DistributedMutex mutex) {
        this.clientMessage = clientMessage;
        if(mutex instanceof SuzukiKasamiDistributedMutex){
            this.mutex = (SuzukiKasamiDistributedMutex) mutex;
        } else{
            AppConfig.timestampedErrorPrint("Error in AskForTokenHandler. Mutex is not Suzuki-Kasami.");
        }
    }

    //SVOJ NIZ TREBA DA UPDEJTUJE RN
    @Override
    public void run() {
        this.handle();
    }

    @Override
    public void handle() {
        long messageTimeStamp = Long.parseLong(clientMessage.getMessageText());
        mutex.updateTimeStamp(messageTimeStamp);

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
                Map<ServantInfo, Integer> rn =  AppConfig.myServantInfo.getRnMap();
                AskForToken askForTokenRequest;

                // Redudantna provera
                if(clientMessage.getMessageType() != MessageType.ASK_FOR_TOKEN) {
                    AppConfig.timestampedErrorPrint("Expected message type ASK_FOR_TOKEN.");
                    return;
                }
                askForTokenRequest = (AskForToken) clientMessage;
                rn.putIfAbsent(senderInfo, 0);

                if(askForTokenRequest.getSm() > rn.get(senderInfo)) {
                    AppConfig.myServantInfo.getRnMap().put(senderInfo, askForTokenRequest.getSm());
                } else {
                    return;
                }


                // Mozemo garantovati da ce svaki cvor dobiti ovu poruku kako svaki cvor ima 4 suseda
                if(AppConfig.myServantInfo.isTokenOwner()) {
                    if(this.mutex.getDistributedMutexInitiated().get()) { //Meni treba mutex.
                        //stavi u queue
                        AppConfig.myServantInfo.getToken().getQueue().add(senderInfo);
                    } else {
                        TokenMessage tokenMessage = new TokenMessage(
                                AppConfig.myServantInfo,
                                null,
                                AppConfig.myServantInfo.getToken().getLnMap(),
                                AppConfig.myServantInfo.getToken().getQueue());
                        //Imam token a ne treba mi, onda tu staviti isTokenOwner = false.
                        AppConfig.timestampedErrorPrint("pre");
                        AppConfig.myServantInfo.setTokenOwner(false);
                        AppConfig.timestampedErrorPrint("posle");
                        if(AppConfig.myServantInfo.getToken().getQueue().peek() == null) { // OVAJ IF DODAT-PROVERITI!
                            tokenMessage.getQueue().add((ServantInfo) this.clientMessage.getOriginalSenderInfo()); //OVA LINIJA DODATA-PROVERITI!
                        }
                        for (ServantInfo neighbour : AppConfig.myServantInfo.getNeighbours()) {
                            MessageUtil.sendMessage(tokenMessage.changeReceiver(neighbour.getId()).makeMeASender());
                        }
                    }
                    return;
                }
                // Ako token nije kod mene onda prosledjujem zahtev.
                AppConfig.timestampedStandardPrint("Rebroadcasting... " + receivedBroadcasts.size());
                for (ServantInfo neighbour : AppConfig.myServantInfo.getNeighbours()) {
                    // Ovaj if postoji zato sto ne zelimo da saljemo istu poruku od onoga od koga smo je primili.
                    if(neighbour.getId() == ((ServantInfo)this.clientMessage.getOriginalSenderInfo()).getId()) {
                        continue;
                    }
                    MessageUtil.sendMessage(clientMessage.changeReceiver(neighbour.getId()).makeMeASender());
                }

            } else {
                //We already got this from somewhere else. /ignore
                AppConfig.timestampedStandardPrint("Already had this. No rebroadcast.");
            }
        }
    }
}
