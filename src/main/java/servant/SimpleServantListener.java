package servant;

import app.AppConfig;
import app.Cancellable;
import servant.handler.MessageDispatcher;
import servant.message.Message;
import servant.message.util.MessageUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleServantListener implements Runnable, Cancellable {
    private static final int ACCEPT_TIMEOUT_MS = 1000;

    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final ServerSocket listenerSocket;
    private volatile boolean working = true;

    public SimpleServantListener() throws IOException {
        this.listenerSocket = new ServerSocket(AppConfig.me().getPort(), 100);
        this.listenerSocket.setSoTimeout(ACCEPT_TIMEOUT_MS);
    }

    @Override
    public void run() {
        try (listenerSocket) {
            while (working) {
                acceptNext();
            }
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Error while closing listener socket: " + e);
        }
        threadPool.shutdown();
    }

    private void acceptNext() {
        try {
            Socket clientSocket = listenerSocket.accept();
            threadPool.submit(() -> receive(clientSocket));
        } catch (SocketTimeoutException e) {
            // The accept timed out; this only gives the loop a chance to check whether the node is still running.
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Error while receiving message: " + e);
        }
    }

    private void receive(Socket clientSocket) {
        Message message = MessageUtil.readMessage(clientSocket);
        if (message != null) {
            MessageDispatcher.onReceive(message);
        }
    }

    @Override
    public void stop() {
        this.working = false;
    }
}
