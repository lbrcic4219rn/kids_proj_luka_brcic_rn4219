package cli.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public abstract class CLICommand {
    protected String commandName;

    public abstract void execute(String args);
}
