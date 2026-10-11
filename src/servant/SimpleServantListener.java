package servant;

import lombok.RequiredArgsConstructor;

import app.AppConfig;
import app.Cancellable;
import mutex.DistributedMutex;
import servant.handler.MessageHandler;
import servant.handler.NullHandler;
import servant.handler.add_new_node_messages.BootstrapSaysHelloHandler;
import servant.handler.add_new_node_messages.SayHiToSystemNodeHandler;
import servant.handler.add_new_node_messages.SystemNodeSaysWelcomeHandler;
import servant.handler.broadcast_messages.AddFileHandler;
import servant.handler.broadcast_messages.DeleteFileHandler;
import servant.handler.broadcast_messages.SystemIsUpdatedHandler;
import servant.handler.broadcast_messages.UpdateSystemHandler;
import servant.handler.health_check_messages.IsOkHandler;
import servant.handler.health_check_messages.NotOkHandler;
import servant.handler.health_check_messages.OkHandler;
import servant.handler.mutex_messages.AskForTokenHandler;
import servant.handler.mutex_messages.TokenMessageHandler;
import servant.handler.poison_messages.PoisonHandler;
import servant.handler.user_request_messages.GiveFileHandler;
import servant.handler.user_request_messages.RequestFileHandler;
import servant.message.Message;
import servant.message.add_new_node_messages.BootstrapSaysHello;
import servant.message.add_new_node_messages.SayHiToSystemNode;
import servant.message.add_new_node_messages.SystemNodeSaysWelcome;
import servant.message.broadcast_messages.UpdateSystem;
import servant.message.util.MessageUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequiredArgsConstructor
public class SimpleServantListener implements Runnable, Cancellable {
    private final ExecutorService threadPool = Executors.newWorkStealingPool();
    private volatile boolean working = true;
    private final DistributedMutex mutex;

    @Override
    public void run() {
        try (ServerSocket listenerSocket = new ServerSocket(AppConfig.myServantInfo.getPort(), 100)) {
            while (working) {
                handleNextMessage(listenerSocket);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void handleNextMessage(ServerSocket listenerSocket) {
        try {
            Socket clientSocket = listenerSocket.accept();
            Message clientMessage = MessageUtil.readMessage(clientSocket);
            threadPool.submit(this.getHandler(clientMessage));
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Error while receiving message: " + e);
        }
    }

    private MessageHandler getHandler(Message clientMessage) {
        // * Note - Nemamo SayHiToBootstrapHandler zato sto se to direktno obradjuje u BootstrapServer-u.
        return switch (clientMessage.getMessageType()) {
            case BOOTSTRAP_SAYS_HELLO -> new BootstrapSaysHelloHandler((BootstrapSaysHello) clientMessage);
            case SAY_HI_TO_SYSTEM_NODE -> new SayHiToSystemNodeHandler((SayHiToSystemNode) clientMessage, this.mutex);
            case SYSTEM_NODE_SAYS_WELCOME -> new SystemNodeSaysWelcomeHandler((SystemNodeSaysWelcome) clientMessage);
            case ADD_FILE -> new AddFileHandler();
            case DELETE_FILE -> new DeleteFileHandler();
            case SYSTEM_IS_UPDATED -> new SystemIsUpdatedHandler();
            case NEW_NODE_ARRIVAL, NODE_REMOVAL, UPDATE_SYSTEM -> new UpdateSystemHandler((UpdateSystem) clientMessage);
            case IS_OK -> new IsOkHandler();
            case NOT_OK -> new NotOkHandler();
            case OK -> new OkHandler();
            case ASK_FOR_TOKEN -> new AskForTokenHandler(clientMessage, this.mutex);
            case TOKEN -> new TokenMessageHandler(clientMessage, this.mutex);
            case POISON -> new PoisonHandler();
            case GIVE_FILE -> new GiveFileHandler();
            case REQUEST_FILE -> new RequestFileHandler();
            default -> new NullHandler(clientMessage);
        };
    }

    @Override
    public void stop() {
        this.working = false;
    }
}
