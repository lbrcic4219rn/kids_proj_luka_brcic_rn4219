package cli.command;

import app.AppConfig;
import app.ServantInfo;

public class InfoCommand extends CLICommand {
    public InfoCommand() {
        super("info");
    }

    @Override
    public void execute(String args) {
        AppConfig.timestampedStandardPrint("My info: " + AppConfig.myServantInfo);
        AppConfig.timestampedStandardPrint("Neighbors:");
        String neighbors = "";
        for (ServantInfo neighbor : AppConfig.myServantInfo.getNeighbours()) {
            neighbors += neighbor.getId() + " ";
        }

        AppConfig.timestampedStandardPrint(neighbors);
        System.out.println((AppConfig.systemState.getFileListMap()));
    }
}
