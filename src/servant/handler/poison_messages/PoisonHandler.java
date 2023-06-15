package servant.handler.poison_messages;

import servant.handler.MessageHandler;

public class PoisonHandler implements MessageHandler {
    @Override
    public void run() {
        this.handle();
    }

    @Override
    public void handle() {

    }
}
