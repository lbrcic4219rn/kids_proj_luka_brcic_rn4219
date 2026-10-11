package cli.command;

import app.AppConfig;
import file.WorkDir;

public class AddCommand extends CLICommand {
    public AddCommand() {
        super("add");
    }

    @Override
    public void execute(String args) {
        if (args == null || args.isBlank()) {
            AppConfig.timestampedErrorPrint("Usage: add name");
            return;
        }
        AppConfig.files().add(WorkDir.normalize(args));
    }
}
