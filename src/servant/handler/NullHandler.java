package servant.handler;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import servant.message.Message;

@RequiredArgsConstructor
public class NullHandler implements MessageHandler {
    private final Message clientMessage;

    @Override
    public void run() {
        AppConfig.timestampedErrorPrint("Couldn't handle message: " + clientMessage);
    }

    @Override
    public void handle() {
        // Nema sta da se obradi - poruka nepoznatog tipa se samo prijavi u run().
    }
}
