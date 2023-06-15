package servant.handler;

import app.AppConfig;
import servant.message.Message;

public class NullHandler implements MessageHandler {
    private final Message clientMessage;

    public NullHandler(Message clientMessage) {
        this.clientMessage = clientMessage;
    }

    @Override
    public void run() {
        AppConfig.timestampedErrorPrint("Couldn't handle message: " + clientMessage);
    }

    @Override
    public void handle() {

    }
}
