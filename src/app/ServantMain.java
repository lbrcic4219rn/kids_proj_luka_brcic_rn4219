package app;

import cli.CLIParser;
import mutex.DistributedMutex;
import mutex.SuzukiKasamiDistributedMutex;
import servant.SimpleServantListener;
import servant.message.Message;
import servant.message.MessageType;
import servant.message.add_new_node_messages.SayHiToBootstrap;
import servant.message.util.DelayedMessageSender;
import servant.message.util.MessageUtil;

import java.util.ArrayList;
import java.util.List;


public class ServantMain {

    /**
     * Pri pokretanju čvora dinamički se dodeljuju port i id u metodi createNewServantInfo.
     * Koristimo portove izmedju 1000 i 2000.
     * */

    public static void main(String[] args) {
        int newServantId = AppConfig.readIdFromProperties();
        AppConfig.myServantInfo = AppConfig.systemState.createNewServantInfo(newServantId);

        String rootDir = AppConfig.readRootDirFromProperties();
        AppConfig.myServantInfo.setRootDir(rootDir);

        startServant();
        askToEnterTheSystem();
    }
    private static void startServant() {
        AppConfig.timestampedStandardPrint("Starting servant " + AppConfig.myServantInfo);

        DistributedMutex mutex = new SuzukiKasamiDistributedMutex();
        //MessageUtil.initializePendingMessages();

        SimpleServantListener simpleListener = new SimpleServantListener(mutex);
        Thread listenerThread = new Thread(simpleListener);
        listenerThread.start();

        List<DelayedMessageSender> senders = new ArrayList<>();

        CLIParser cliParser = new CLIParser(simpleListener, senders, mutex);
        Thread cliThread = new Thread(cliParser);
        cliThread.start();
    }
    private static void askToEnterTheSystem() {
        Message hi = new SayHiToBootstrap(MessageType.SAY_HI_TO_BOOTSTRAP, AppConfig.myServantInfo,
                BootstrapServer.getInstance(), "Hi Bootstrap, I would like to enter the system.");
        MessageUtil.sendMessage(hi);
    }
}
