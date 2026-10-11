package servant.handler.add_new_node_messages;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import app.ServantInfo;
import servant.handler.MessageHandler;
import servant.message.Message;
import servant.message.MessageType;
import servant.message.add_new_node_messages.BootstrapSaysHello;
import servant.message.add_new_node_messages.SayHiToSystemNode;
import servant.message.util.MessageUtil;

// Ovaj handler znaci: sta se radi kada se primi poruka BootstrapSaysHelloHandler.
@RequiredArgsConstructor
public class BootstrapSaysHelloHandler implements MessageHandler {
    private final BootstrapSaysHello message;

    @Override
    public void run() {
        try {
            this.handle();
        } catch (Exception e) {
            AppConfig.timestampedErrorPrint("Error while handling message " + message + ": " + e);
        }
    }

    @Override
    public void handle() {
        //Sad ovaj node, koji je primio ovu poruku, treba da se obrati sistemskom node-u koji se vrti na portu koji je BootstrapServer prosledio.
        int portOfSystemNodeToAsk = message.getPortOfSystemNodeToAsk();
        ServantInfo systemNodeToAsk = new ServantInfo(AppConfig.HOST, portOfSystemNodeToAsk);
        Message hi = new SayHiToSystemNode(MessageType.SAY_HI_TO_SYSTEM_NODE, AppConfig.myServantInfo, systemNodeToAsk,
                "Hi system node, bootstrap said I should ask you to enter the system.");
        MessageUtil.sendMessage(hi);
    }
}
