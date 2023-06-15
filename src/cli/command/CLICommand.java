package cli.command;

public abstract class CLICommand {
    protected String commandName;

    public CLICommand(String commandName) {
        this.commandName = commandName;
    }

    public abstract void execute(String args);

    public String getCommandName() {
        return commandName;
    }
}
