package servant.handler.health_check_messages;

import servant.handler.MessageHandler;

public class OkHandler implements MessageHandler {
    @Override
    public void run() {
        this.handle();
    }

    @Override
    public void handle() {

    }
}
