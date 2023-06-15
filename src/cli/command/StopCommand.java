package cli.command;

import app.AppConfig;
import cli.CLIParser;
import servant.SimpleServantListener;
import servant.message.util.DelayedMessageSender;

import java.util.List;

public class StopCommand extends CLICommand {
    private CLIParser parser;
    private SimpleServantListener listener;
    private List<DelayedMessageSender> senderWorkers;

    public StopCommand(CLIParser parser, SimpleServantListener listener, List<DelayedMessageSender> senderWorkers) {
        super("stop");
        this.parser = parser;
        this.listener = listener;
        this.senderWorkers = senderWorkers;
    }

    @Override
    public void execute(String args) {
        AppConfig.timestampedStandardPrint("Stopping...");
        parser.stop();
        listener.stop();
    }
}
