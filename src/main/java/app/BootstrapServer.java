package app;

import servant.message.Message;
import servant.message.MessageType;
import servant.message.util.MessageUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class BootstrapServer {
    private static final Random RANDOM = new SecureRandom();
    private static final int DEFAULT_PORT = 1000;

    private final List<ServantInfo> activeServants = new ArrayList<>();
    private volatile boolean working = true;

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        String ip = args.length > 1 ? args[1] : "localhost";
        AppConfig.initBootstrap(ip, port);
        new BootstrapServer().start(port);
    }

    private void start(int port) {
        new Thread(this::readCommands, "cli").start();
        try (ServerSocket listenerSocket = new ServerSocket(port)) {
            listenerSocket.setSoTimeout(1000);
            AppConfig.timestampedStandardPrint("Bootstrap server started on port: " + port);
            while (working) {
                acceptNext(listenerSocket);
            }
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Couldn't open bootstrap port " + port + ": " + e.getMessage());
        }
        MessageUtil.shutdown();
        System.exit(0);
    }

    private void acceptNext(ServerSocket listenerSocket) {
        try {
            Message message = MessageUtil.readMessage(listenerSocket.accept());
            if (message != null) {
                handle(message);
            }
        } catch (SocketTimeoutException e) {
            // The accept timed out; this only gives the loop a chance to check whether the server is still running.
        } catch (IOException | RuntimeException e) {
            AppConfig.timestampedErrorPrint("Error while handling message: " + e);
        }
    }

    private void handle(Message message) {
        AppConfig.timestampedStandardPrint("Got " + message);
        ServantInfo sender = message.getSender();
        switch (message.getType()) {
            case SAY_HI_TO_BOOTSTRAP -> {
                activeServants.removeIf(servant -> servant.getKey().equals(sender.getKey()));
                ServantInfo contact = null;
                if (activeServants.isEmpty()) {
                    activeServants.add(sender);
                } else {
                    contact = activeServants.get(RANDOM.nextInt(activeServants.size()));
                }
                MessageUtil.reply(message, MessageType.BOOTSTRAP_SAYS_HELLO, contact);
            }
            case JOINED_SYSTEM -> {
                if (!activeServants.contains(sender)) {
                    activeServants.add(sender);
                }
            }
            case LEFT_SYSTEM -> activeServants.remove(message.<ServantInfo>payload());
            default -> AppConfig.timestampedErrorPrint("Bootstrap ignores message of type " + message.getType());
        }
        AppConfig.timestampedStandardPrint("Active servants: " + activeServants);
    }

    private void readCommands() {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            if (scanner.nextLine().trim().equals("stop")) {
                working = false;
                return;
            }
        }
    }
}
