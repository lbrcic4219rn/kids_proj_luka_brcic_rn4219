package cli;

import app.AppConfig;
import app.Cancellable;
import cli.command.AddCommand;
import cli.command.CLICommand;
import cli.command.CommitCommand;
import cli.command.InfoCommand;
import cli.command.PullCommand;
import cli.command.QuitCommand;
import cli.command.RemoveCommand;

import java.util.List;
import java.util.Scanner;

public class CLIParser implements Runnable, Cancellable {
    private final Scanner scanner = new Scanner(System.in);
    private final List<CLICommand> commandList;
    private volatile boolean working = true;

    public CLIParser() {
        this.commandList = List.of(new InfoCommand(), new AddCommand(), new PullCommand(),
                new CommitCommand(this::readLine), new RemoveCommand(), new QuitCommand("quit"), new QuitCommand("stop"));
    }

    private String readLine() {
        return scanner.hasNextLine() ? scanner.nextLine() : null;
    }

    @Override
    public void run() {
        while (working) {
            String commandLine = readLine();
            if (commandLine == null) {
                return;
            }
            commandLine = commandLine.trim();
            if (!commandLine.isEmpty()) {
                execute(commandLine);
            }
        }
    }

    private void execute(String commandLine) {
        int spacePos = commandLine.indexOf(' ');
        String commandName = spacePos == -1 ? commandLine : commandLine.substring(0, spacePos);
        String commandArgs = spacePos == -1 ? null : commandLine.substring(spacePos + 1);
        for (CLICommand cliCommand : commandList) {
            if (cliCommand.getCommandName().equals(commandName)) {
                try {
                    cliCommand.execute(commandArgs);
                } catch (RuntimeException e) {
                    AppConfig.timestampedErrorPrint("Command failed: " + commandLine, e);
                }
                return;
            }
        }
        AppConfig.timestampedErrorPrint("Unknown command: " + commandName);
    }

    @Override
    public void stop() {
        this.working = false;
    }
}
