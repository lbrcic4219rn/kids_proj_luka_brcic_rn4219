package servant.message.util;

import app.AppConfig;
import app.ServantInfo;
import servant.message.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class MessageUtil {
    public static Map<Integer, BlockingQueue<Message>> pendingMessages = new ConcurrentHashMap<>();
    public static void initializePendingMessages() {
        for(ServantInfo neighbour : AppConfig.myServantInfo.getNeighbours()) {
            pendingMessages.put(neighbour.getId(), new LinkedBlockingQueue<>());
        }
    }
    public static Message readMessage(Socket socket) {
        Message clientMessage = null;
        ObjectInputStream ois;
        try {
            ois = new ObjectInputStream(socket.getInputStream());
            clientMessage = (Message) ois.readObject();
            socket.close();
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Error in reading socket on " + socket.getInetAddress() + ":" + socket.getPort());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        AppConfig.timestampedStandardPrint("Got message " + clientMessage);
        return clientMessage;
    }
    public static void sendMessage(Message message) {
        Thread delayedSender = new Thread(new DelayedMessageSender(message));
        delayedSender.start();
    }
}
