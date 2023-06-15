package app;

import servant.message.Message;
import servant.message.MessageType;
import servant.message.add_new_node_messages.BootstrapSaysHello;
import servant.message.add_new_node_messages.SystemNodeSaysWelcome;
import servant.message.mutex_messages.TokenMessage;
import servant.message.util.MessageUtil;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Kod nas će ip adresa uvek biti localhost. Bootstrap će se uvek vrteti na portu 1000. To je port rezervisan za njega.
 * Ostali čvorovi(čvorovi sistema će se vrteti na portovima 1001-2000).
 */

public class BootstrapServer implements Receiver, Sender, Serializable {
    private static BootstrapServer instance;
    private String ipAddress;
    private int port;
    private volatile boolean working;
    private List<Integer> activeServantsPorts;

    private BootstrapServer() {
        ipAddress = "localhost";
        port = 1000;
        working = true;
        activeServantsPorts = new ArrayList<>();
    }

    public static BootstrapServer getInstance() {
        if (instance == null) {
            instance = new BootstrapServer();
        }
        return instance;
    }

    @Override
    public String getIpAddress() {
        return this.ipAddress;
    }

    @Override
    public int getPort() {
        return this.port;
    }

    private class CLIWorker implements Runnable {
        @Override
        public void run() {
            Scanner sc = new Scanner(System.in);

            String line;
            while (true) {
                line = sc.nextLine();

                if (line.equals("stop")) {
                    working = false;
                    break;
                }
            }

            sc.close();
        }
    }

    public void startBootstrapServer() {
        Thread cliThread = new Thread(new CLIWorker());
        cliThread.start();

//        ServerSocket listenerSocket = null;
//        try {
//            listenerSocket = new ServerSocket(this.getPort());
//            listenerSocket.setSoTimeout(1000);
//        } catch (IOException e1) {
//            AppConfig.timestampedErrorPrint("Problem while opening listener socket.");
//            System.exit(0);
//        }

        ServerSocket listenerSocket = null;
        try {
            listenerSocket = new ServerSocket(this.getPort());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        while (working) {
            try {
                Socket newServantSocket = listenerSocket.accept();
                ObjectInputStream ois = new ObjectInputStream(newServantSocket.getInputStream());
                Message message = (Message) ois.readObject();

                if (!message.getMessageType().equals(MessageType.SAY_HI_TO_BOOTSTRAP)) { // Bootstrap moze da obradjuje samo jedan tip poruke.
                    AppConfig.timestampedErrorPrint("Error while sending message " + message + "Only \"SAY_HI_TO_BOOTSTRAP\" message is allowed to be sent to Bootstrap.");
                }

                AppConfig.timestampedStandardPrint("Bootstrap server received message: " + message);

                ServantInfo messageSender = (ServantInfo) message.getOriginalSenderInfo();

                // Bootstrap bira neki od system node-ova i informacije o njemu salje u poruci. Upucuje novi cvor da se obrati cvoru na tom portu.

                if (activeServantsPorts.size() > 0) {
                    int portOfSystemNodeToAsk = this.chooseRandomPortFromList();
                    Message hello = new BootstrapSaysHello(MessageType.BOOTSTRAP_SAYS_HELLO, this, messageSender,
                            "Hello, You should talk to system node on port " + portOfSystemNodeToAsk, portOfSystemNodeToAsk);
                    MessageUtil.sendMessage(hello);
                } else {
                    // Ako je activeServantsPorts.size() = 0, to znaci da je ovo prvi node koji se javlja bootstrap-u tj. da je sistem prazan.
                    // U tom slucaju "zaobilazimo" uobicajeni protokol, odmah dodajemo novi cvor u sistem i saljemo mu poruku.
                    ServantInfo newNode = (ServantInfo) message.getOriginalSenderInfo();

                    newNode.setTokenOwner(true); // Posto je ovo prvi node u sistemu, njemu damo da inicijalno ima token.
                    TokenMessage tokenMessage = new TokenMessage(
                            newNode, //inicijalno newNode je i sender i receiver tokena
                            newNode,
                            new HashMap<>(),
                            new LinkedList<>());
                    newNode.setToken(tokenMessage);

                    AppConfig.systemState.addNewNodeInSystem(newNode);
                    Message welcome = new SystemNodeSaysWelcome(MessageType.SYSTEM_NODE_SAYS_WELCOME, instance,
                            newNode, "Welcome new node with id " + newNode.getId(), AppConfig.systemState);
                    MessageUtil.sendMessage(welcome);
                }
                // Bootstrap update-uje listu portova sa aktivnim cvorovima.
                this.activeServantsPorts.add(messageSender.getPort());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        BootstrapServer bs = getInstance();
        AppConfig.timestampedStandardPrint("Bootstrap server started on port: " + bs.getPort());
        bs.startBootstrapServer();
    }

    private int chooseRandomPortFromList() {
        Random random = new Random();
        int listSize = activeServantsPorts.size();
        int randomIndex = random.nextInt(listSize);
        return activeServantsPorts.get(randomIndex);
    }
}
