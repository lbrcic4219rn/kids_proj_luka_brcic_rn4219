package cli.command;

import app.AppConfig;
import file.WorkDir;

public class RemoveCommand extends CLICommand {
    public RemoveCommand() {
        super("remove");
    }

    @Override
    public void execute(String args) {
        if (args == null || args.isBlank()) {
            AppConfig.timestampedErrorPrint("Usage: remove name");
            return;
        }
        AppConfig.files().remove(WorkDir.normalize(args));
    }
}
