package app;

import cli.CLIParser;
import servant.SimpleServantListener;
import servant.handler.MessageDispatcher;
import servant.message.Message;
import servant.message.MessageType;
import servant.message.util.MessageUtil;

import java.io.IOException;

public class ServantMain {
    private static final long BOOTSTRAP_TIMEOUT_MS = 5000;
    private static final long WELCOME_TIMEOUT_MS = 30000;
    private static final int JOIN_ATTEMPTS = 10;

    private static SimpleServantListener listener;
    private static CLIParser cliParser;

    public static void main(String[] args) {
        if (args.length != 1) {
            AppConfig.timestampedErrorPrint("Usage: ServantMain <config file>");
            System.exit(1);
        }
        try {
            AppConfig.initServant(args[0]);
            listener = new SimpleServantListener();
        } catch (IOException | IllegalArgumentException e) {
            AppConfig.timestampedErrorPrint("Couldn't start servant: " + e.getMessage());
            System.exit(1);
        }
        AppConfig.timestampedStandardPrint("Starting servant " + AppConfig.me());
        MessageUtil.setLocalDelivery(MessageDispatcher::deliver);
        new Thread(listener, "listener").start();
        cliParser = new CLIParser();
        new Thread(cliParser, "cli").start();

        if (!enterTheSystem()) {
            AppConfig.timestampedErrorPrint("Couldn't enter the system. Exiting...");
            System.exit(1);
        }
        new Thread(AppConfig.detector(), "failure-detector").start();
    }

    private static boolean enterTheSystem() {
        for (int attempt = 0; attempt < JOIN_ATTEMPTS; attempt++) {
            Message hello = MessageUtil.requestDirect(MessageType.SAY_HI_TO_BOOTSTRAP, AppConfig.bootstrap(), null,
                    BOOTSTRAP_TIMEOUT_MS);
            if (hello == null) {
                AppConfig.timestampedErrorPrint("Bootstrap at " + AppConfig.bootstrap() + " does not respond.");
                pause();
                continue;
            }
            ServantInfo contact = hello.payload();
            if (contact == null) {
                AppConfig.state().addNode(AppConfig.me());
                AppConfig.mutex().createInitialToken();
                AppConfig.setJoined(true);
                AppConfig.timestampedStandardPrint("I am the first node in the system.");
                return true;
            }
            Message welcome = MessageUtil.requestDirect(MessageType.SAY_HI_TO_SYSTEM_NODE, contact, null, WELCOME_TIMEOUT_MS);
            if (welcome != null) {
                MembershipManager.applyWelcome(welcome.payload());
                AppConfig.setJoined(true);
                MessageUtil.sendDirect(MessageType.JOINED_SYSTEM, AppConfig.bootstrap(), null);
                AppConfig.timestampedStandardPrint("Great, I'm in the system now. Nodes in system: "
                        + AppConfig.state().getMembers().size());
                return true;
            }
            AppConfig.timestampedErrorPrint("Node " + contact + " did not let me in, asking bootstrap again.");
            MessageUtil.sendDirect(MessageType.LEFT_SYSTEM, AppConfig.bootstrap(), contact);
            pause();
        }
        return false;
    }

    private static void pause() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void quit() {
        MembershipManager.leave();
        AppConfig.detector().stop();
        cliParser.stop();
        MessageUtil.shutdown();
        listener.stop();
        AppConfig.timestampedStandardPrint("Stopped.");
        System.exit(0);
    }
}
