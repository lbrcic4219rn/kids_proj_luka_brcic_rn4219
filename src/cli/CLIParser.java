package cli;

import app.AppConfig;
import app.Cancellable;
import cli.command.*;
import mutex.DistributedMutex;
import servant.SimpleServantListener;
import servant.message.util.DelayedMessageSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CLIParser implements Runnable, Cancellable {
    private volatile boolean working = true;
    private final List<CLICommand> commandList;
    public CLIParser(SimpleServantListener listener, List<DelayedMessageSender> senderThreads, DistributedMutex mutex) {
        this.commandList = new ArrayList<>();

        commandList.add(new InfoCommand());
        commandList.add(new AddCommand(mutex));
        commandList.add(new PullCommand(mutex));
        commandList.add(new RemoveCommand(mutex));
        commandList.add(new StopCommand(this, listener, senderThreads));
    }

    @Override
    public void run() {
        Scanner sc = new Scanner(System.in);

        while (working) {
            String commandLine = sc.nextLine();

            int spacePos = commandLine.indexOf(" ");

            String commandName;
            String commandArgs = null;
            if (spacePos != -1) {
                commandName = commandLine.substring(0, spacePos);
                commandArgs = commandLine.substring(spacePos+1, commandLine.length());
            } else {
                commandName = commandLine;
            }

            boolean found = false;

            for (CLICommand cliCommand : commandList) {
                if (cliCommand.getCommandName().equals(commandName)) {
                    cliCommand.execute(commandArgs);
                    found = true;
                    break;
                }
            }

            if (!found) {
                AppConfig.timestampedErrorPrint("Unknown command: " + commandName);
            }
        }

        sc.close();
    }

    @Override
    public void stop() {
        this.working = false;
    }
}
