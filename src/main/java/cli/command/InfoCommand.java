package cli.command;

import app.AppConfig;
import app.ServantInfo;

public class InfoCommand extends CLICommand {
    public InfoCommand() {
        super("info");
    }

    @Override
    public void execute(String args) {
        AppConfig.timestampedStandardPrint("My info: " + AppConfig.me() + ", " + AppConfig.mutex().describe()
                + "\nNodes in system (ring order): " + AppConfig.state().getMembers()
                + "\nNeighbours: " + AppConfig.state().getNeighbours().stream().map(ServantInfo::getKey).toList()
                + "\n" + AppConfig.files().describe());
    }
}
