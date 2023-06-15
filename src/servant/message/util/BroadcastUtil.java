package servant.message.util;

import app.AppConfig;
import app.ServantInfo;
import servant.handler.BroadcastHandler;
import servant.message.Message;

import java.util.List;

public class BroadcastUtil {
    public static void sendBroadcastMessage(List<ServantInfo> neighbours, Message message) {
        for(ServantInfo neighbour: neighbours) {
            MessageUtil.sendMessage(message.changeReceiver(neighbour.getId()).makeMeASender());
        }
    }
    public static void sendBroadcastMessageAndExcludeNodeWithId(List<ServantInfo> neighbours, Message message, int excludeId) {
        for(ServantInfo neighbour: neighbours) {
            if(neighbour.getId() == excludeId) {
                continue;
            }
            MessageUtil.sendMessage(message.changeReceiver(neighbour.getId()).makeMeASender());
        }
    }

    // Ova metoda enkapsulira opstu logiku za obradu primljene broadcast poruke.
    // Konkretna logika za obradu poruke(ukoliko je to potrebno i do toga dodje) se nalazi u handle metodi od prosledjenog handler-a.
    public static void processReceivedBroadcastMessage(Message message, BroadcastHandler handler) {
        ServantInfo senderInfo = (ServantInfo) message.getOriginalSenderInfo();
//        ServantInfo lastSenderInfo = message.getRoute().size() == 0 ?
//                (ServantInfo) message.getOriginalSenderInfo() :
//                message.getRoute().get(message.getRoute().size()-1);
//        String text = String.format("Got %s from %s broadcast by %s",
//                message.getMessageText(), lastSenderInfo, senderInfo);
//        AppConfig.timestampedStandardPrint(text);

        if (senderInfo.getId() == AppConfig.myServantInfo.getId()) {
            //We are the sender :o someone bounced this back to us. /ignore
            AppConfig.timestampedStandardPrint("Got own message back. No rebroadcast.");
        } else {
            //Try to put in the set. Thread safe add ftw.
            boolean didPut = handler.getReceivedBroadcasts().add(message);

            if (didPut) {
                //Logika za obradu ove poruke se nalazi u handle metodi od prosledjenog handler-a.
                handler.handle();

                AppConfig.timestampedStandardPrint("Rebroadcasting... " + handler.getReceivedBroadcasts().size());
                for (ServantInfo neighbour : AppConfig.myServantInfo.getNeighbours()) {
                    //Same tokenMessage, different receiver, and add us to the route table.
                    MessageUtil.sendMessage(message.changeReceiver(neighbour.getId()).makeMeASender());
                }
            } else {
                //We already got this from somewhere else. /ignore
                AppConfig.timestampedStandardPrint("Already had this. No rebroadcast.");
            }
        }
    }
}
