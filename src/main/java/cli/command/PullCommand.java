package cli.command;

import app.AppConfig;
import file.WorkDir;

public class PullCommand extends CLICommand {
    public PullCommand() {
        super("pull");
    }

    @Override
    public void execute(String args) {
        if (args == null || args.isBlank()) {
            AppConfig.timestampedErrorPrint("Usage: pull name [version]");
            return;
        }
        String[] parts = args.trim().split("\\s+");
        Integer version = null;
        if (parts.length > 1) {
            try {
                version = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                AppConfig.timestampedErrorPrint("Version must be a number: " + parts[1]);
                return;
            }
        }
        AppConfig.files().pull(WorkDir.normalize(parts[0]), version);
    }
}
