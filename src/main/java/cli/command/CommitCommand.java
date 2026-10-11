package cli.command;

import app.AppConfig;
import file.WorkDir;

import java.util.function.Supplier;

public class CommitCommand extends CLICommand {
    private final Supplier<String> input;

    public CommitCommand(Supplier<String> input) {
        super("commit");
        this.input = input;
    }

    @Override
    public void execute(String args) {
        if (args == null || args.isBlank()) {
            AppConfig.timestampedErrorPrint("Usage: commit name");
            return;
        }
        AppConfig.files().commit(WorkDir.normalize(args), input);
    }
}
