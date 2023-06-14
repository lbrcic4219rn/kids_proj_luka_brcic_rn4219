package cli.command;

import app.AppConfig;
import servent.message.BroadcastMessage;
import servent.message.Message;
import servent.message.util.MessageUtil;

public class BroadcastCommand implements CLICommand {

    @Override
    public String commandName() {
        return "broadcast";
    }

    @Override
    public void execute(String args) {
        String msgToSend = "";

        msgToSend = args;

        if (args == null) {
            AppConfig.timestampedErrorPrint("No message to broadcast");
            return;
        }

        Message broadcastMessage = new BroadcastMessage(AppConfig.myServentInfo, null, msgToSend);
        for (Integer neighbor : AppConfig.myServentInfo.getNeighbors()) {
            /*
             * It is important to modify the existing message, and not create a new one.
             *
             * If we do new BroadcastMessage(...) the message id will change,
             * and thus, different neighbors will get non-equal messages, and
             * there will be unnecessary rebroadcast.
             */
            broadcastMessage = broadcastMessage.changeReceiver(neighbor);

            MessageUtil.sendMessage(broadcastMessage);
        }

    }
}
