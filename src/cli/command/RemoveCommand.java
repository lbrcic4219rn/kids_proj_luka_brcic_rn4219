package cli.command;

import app.AppConfig;
import mutex.DistributedMutex;
import mutex.SuzukiKasamiDistributedMutex;
import servant.message.MessageType;
import servant.message.broadcast_messages.UpdateSystem;
import servant.message.util.BroadcastUtil;
import servant.message.util.FileUtil;

import java.util.ArrayList;
import java.util.List;

public class RemoveCommand extends CLICommand{

    private SuzukiKasamiDistributedMutex mutex;
    public RemoveCommand(DistributedMutex mutex) {
        super("remove");
        this.mutex = (SuzukiKasamiDistributedMutex) mutex;
    }

    @Override
    public void execute(String args) {

        if (args == null || args.isEmpty()) {
            AppConfig.timestampedStandardPrint("Invalid argument for add command. Should be add path.");
            return;
        }

        this.mutex.lock();
        Integer myId = AppConfig.myServantInfo.getId();
        List<String> fileList = AppConfig.systemState.getFileListMap().get(myId);
        if(fileList == null) {
            AppConfig.timestampedStandardPrint("no such file");
            this.mutex.unlock();
        }

        List<String> filesToDelete = new ArrayList<>();
        boolean fileFound = false;

        for (String s : fileList) {
            if (s.equals(args)) {
                filesToDelete.add(s);
                fileFound = true;
            }
        }

        for (String fileToDelete : filesToDelete) {
            FileUtil.deleteFile(fileToDelete, myId);
        }

        if(!fileFound) {
            System.out.println("no such file or folder");
            this.mutex.unlock();
        }
        UpdateSystem message = new UpdateSystem(MessageType.UPDATE_SYSTEM, AppConfig.myServantInfo,
                null, "File has been removed.", AppConfig.systemState);
        BroadcastUtil.sendBroadcastMessage(AppConfig.myServantInfo.getNeighbours(), message);
        this.mutex.unlock();
    }
}
