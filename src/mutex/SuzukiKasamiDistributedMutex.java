package mutex;

import lombok.Getter;

import app.AppConfig;
import app.ServantInfo;
import servant.message.mutex_messages.AskForToken;
import servant.message.mutex_messages.TokenMessage;
import servant.message.util.MessageUtil;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SuzukiKasamiDistributedMutex implements DistributedMutex {
    @Getter
    private final AtomicBoolean distributedMutexInitiated = new AtomicBoolean(false);
    private final AtomicLong timeStamp;

    public SuzukiKasamiDistributedMutex() {
        this.timeStamp = new AtomicLong(AppConfig.myServantInfo.getId());

    }

    public void updateTimeStamp(long newTimeStamp) {
        long currentTimeStamp = timeStamp.get();

        while (newTimeStamp > currentTimeStamp) {
            if (timeStamp.compareAndSet(currentTimeStamp, newTimeStamp + 1)) {
                break;
            }
            currentTimeStamp = timeStamp.get();
        }
    }

    // Namerno prozivanje (polling): vlasnistvo nad tokenom stize porukom, a AppConfig.myServantInfo se zamenjuje pri svakom update-u sistema.
    @SuppressWarnings("BusyWait")
    @Override
    public void lock() {
        while (!distributedMutexInitiated.compareAndSet(false, true)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to initiate distributed mutex.", e);
            }
        }
        if (!AppConfig.myServantInfo.isTokenOwner()) {
            AskForToken askForTokenRequest = new AskForToken(AppConfig.myServantInfo,
                    null, timeStamp.get(), AppConfig.myServantInfo.getSm().incrementAndGet());

            for (ServantInfo neighbour : AppConfig.myServantInfo.getNeighbours()) {
                MessageUtil.sendMessage(askForTokenRequest.changeReceiver(neighbour.getId()).makeMeASender());
            }
        }

        while (!AppConfig.myServantInfo.isTokenOwner()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                distributedMutexInitiated.set(false);
                throw new IllegalStateException("Interrupted while waiting for token.", e);
            }
        }
    }

    @Override
    public void unlock() {

        TokenMessage token = AppConfig.myServantInfo.getToken();

        //Azuriramo tokenovu mapu sa brojem poziva koje smo izvrsili
        token.getLnMap().put(AppConfig.myServantInfo, AppConfig.myServantInfo.getRnMap().get(AppConfig.myServantInfo));

        if (token.getQueue().peek() == null) token.getQueue().add(AppConfig.myServantInfo);
        token.getQueue().remove();

        TokenMessage tokenMessage = new TokenMessage(AppConfig.myServantInfo,
                null, token.getLnMap(), token.getQueue());

        AppConfig.myServantInfo.getRnMap().forEach((key, value) -> {
            token.getLnMap().putIfAbsent(key, 0);
            if (value == token.getLnMap().get(key) + 1) {
                token.getQueue().add(key);
            }
        });

        if (token.getQueue().peek() != null) {
            AppConfig.timestampedErrorPrint("unlock");
            AppConfig.myServantInfo.setTokenOwner(false);
            for (ServantInfo neighbour : AppConfig.myServantInfo.getNeighbours()) {
                MessageUtil.sendMessage(tokenMessage.changeReceiver(neighbour.getId()).makeMeASender());
            }
        }
        distributedMutexInitiated.set(false);
    }
}
