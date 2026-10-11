package servant.message.util;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import app.Receiver;
import servant.message.Message;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
public class DelayedMessageSender implements Runnable {
    private final Message messageToSend;

    @Override
    public void run() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1000) + 500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AppConfig.timestampedErrorPrint("Interrupted before sending message: " + messageToSend);
            return;
        }

        Receiver receiverInfo = messageToSend.getReceiverInfo();
        AppConfig.timestampedStandardPrint("Sending message " + messageToSend);

        try (Socket sendSocket = new Socket(receiverInfo.getIpAddress(), receiverInfo.getPort())) {
            ObjectOutputStream oos = new ObjectOutputStream(sendSocket.getOutputStream());
            oos.writeObject(messageToSend);
            oos.flush();
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Couldn't send message: " + messageToSend + " (" + e + ")");
        }
    }
}
