package servant.message.util;

import app.AppConfig;
import app.Receiver;
import servant.message.Message;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class DelayedMessageSender implements Runnable{
    private Message messageToSend;

    public DelayedMessageSender(Message messageToSend) {
        this.messageToSend = messageToSend;
    }

    @Override
    public void run() {
        try {
            Thread.sleep((long)(Math.random() * 1000) + 500);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }

        Receiver receiverInfo = messageToSend.getReceiverInfo();
        AppConfig.timestampedStandardPrint("Sending message " + messageToSend);

        try {
            Socket sendSocket = new Socket(receiverInfo.getIpAddress(), receiverInfo.getPort());

            ObjectOutputStream oos = new ObjectOutputStream(sendSocket.getOutputStream());
            oos.writeObject(messageToSend);
            oos.flush();

            sendSocket.close();

        } catch (IOException e) {
            e.printStackTrace();
            AppConfig.timestampedErrorPrint("Couldn't send message: " + messageToSend.toString());
        }
    }
}
