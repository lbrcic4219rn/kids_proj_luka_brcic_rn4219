package cli.command;

import app.AppConfig;
import app.SystemState;
import mutex.DistributedMutex;
import mutex.SuzukiKasamiDistributedMutex;
import servant.message.MessageType;
import servant.message.broadcast_messages.UpdateSystem;
import servant.message.util.BroadcastUtil;
import servant.message.util.FileUtil;

import java.util.List;

public class AddCommand extends CLICommand{

    private SuzukiKasamiDistributedMutex mutex;
    public AddCommand(DistributedMutex mutex) {
        super("add");
        this.mutex = (SuzukiKasamiDistributedMutex) mutex;
    }

    @Override
    public void execute(String args) {
        if (args == null || args.isEmpty()) {
            AppConfig.timestampedStandardPrint("Invalid argument for add command. Should be add path.");
            return;
        }
        System.out.println(args);
        String[] pathParts = args.split("\\\\");
        String fileName = pathParts[pathParts.length - 1];

        this.mutex.lock();

        boolean fileExists = FileUtil.checkIfFileExists(fileName);
        if(fileExists) {
            AppConfig.timestampedStandardPrint("File already exists in system");
            this.mutex.unlock();
            return;
        }

        FileUtil.addFile(args, fileName);

        // Saljemo poruku za update sistema
        UpdateSystem message = new UpdateSystem(MessageType.UPDATE_SYSTEM, AppConfig.myServantInfo,
                null, "New file has been added.", AppConfig.systemState);
        BroadcastUtil.sendBroadcastMessage(AppConfig.myServantInfo.getNeighbours(), message);

        this.mutex.unlock();

    }
}
