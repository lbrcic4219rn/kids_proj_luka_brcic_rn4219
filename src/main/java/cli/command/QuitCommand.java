package cli.command;

import app.AppConfig;
import app.ServantMain;

public class QuitCommand extends CLICommand {
    public QuitCommand(String commandName) {
        super(commandName);
    }

    @Override
    public void execute(String args) {
        AppConfig.timestampedStandardPrint("Leaving the system...");
        ServantMain.quit();
    }
}
