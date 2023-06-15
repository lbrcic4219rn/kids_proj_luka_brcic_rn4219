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
import java.util.Map;

public class PullCommand extends CLICommand{

    private SuzukiKasamiDistributedMutex mutex;
    public PullCommand(DistributedMutex mutex) {
        super("pull");
        this.mutex = (SuzukiKasamiDistributedMutex) mutex;
    }

    @Override
    public void execute(String args) {
        if (args == null || args.isEmpty()) {
            AppConfig.timestampedStandardPrint("Invalid argument for add command. Should be add path.");
            return;
        }

        this.mutex.lock();
        String rootDir = AppConfig.myServantInfo.getRootDir();
        String sourceDir = rootDir.substring(0, rootDir.length() - 1);

        boolean fileExists = FileUtil.checkIfFileExists(args);

        if(!fileExists) {
            AppConfig.timestampedStandardPrint("File doesnt exist in system");
            this.mutex.unlock();
            return;
        }

        //trazimo vlasnika fajla
        Map<Integer, List<String>> fileListMap = AppConfig.systemState.getFileListMap();
        Integer ownerId = 0;
        for (Integer key : fileListMap.keySet()) {
            List<String> fileList = fileListMap.get(key);
            if(fileList != null) {
                for(String s: fileList){
                    if(s.equals(args))
                        ownerId = key;
                }
            }
        }

        //ruta do fajla
        FileUtil.addFile(sourceDir + ownerId + "\\" + args ,args);

        List<String> fileList = AppConfig.systemState.getFileListMap().get(ownerId);
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
            FileUtil.deleteFile(fileToDelete, ownerId);
        }

        if(!fileFound) {
            System.out.println("no such file or folder");
            this.mutex.unlock();
        }
        UpdateSystem message = new UpdateSystem(MessageType.UPDATE_SYSTEM, AppConfig.myServantInfo,
                null, "File has been pulled.", AppConfig.systemState);
        BroadcastUtil.sendBroadcastMessage(AppConfig.myServantInfo.getNeighbours(), message);
        this.mutex.unlock();
    }
}
