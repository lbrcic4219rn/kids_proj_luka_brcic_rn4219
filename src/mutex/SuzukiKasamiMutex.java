package mutex;

import app.AppConfig;
import servent.message.mutex.*;
import servent.message.util.MessageUtil;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SuzukiKasamiMutex implements DistributedMutex {

    private AtomicBoolean distributedMutexInitiated = new AtomicBoolean(false);
    private AtomicLong timeStamp = null;

    public SuzukiKasamiMutex() {
        this.timeStamp = new AtomicLong(AppConfig.myServentInfo.getId());

    }

    public void updateTimeStamp(long newTimeStamp){
        long currentTimeStamp = timeStamp.get();

        while(newTimeStamp > currentTimeStamp){
            if(timeStamp.compareAndSet(currentTimeStamp, newTimeStamp+1)){
                break;
            }
            currentTimeStamp = timeStamp.get();
        }
    }

    @Override
    public void lock() {
        while (!distributedMutexInitiated.compareAndSet(false, true)){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        AppConfig.timestampedStandardPrint("Prosao saljem poruke");
        if(!AppConfig.myServentInfo.isHasToken()) {
            SKRequestMessage skRequestMessage = new SKRequestMessage(AppConfig.myServentInfo,
                    null, timeStamp.get(), AppConfig.myServentInfo.getSm().incrementAndGet());

            for(int i = 0; i< AppConfig.myServentInfo.getNeighbors().size(); i++){
                int neighborId = AppConfig.myServentInfo.getNeighbors().get(i);

                MessageUtil.sendMessage(skRequestMessage.changeReceiver(neighborId).makeMeASender());
            }
        }


        while(true){
            if(AppConfig.myServentInfo.isHasToken()){
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void unlock() {

        SKTokenMessage token = AppConfig.myServentInfo.getToken();

        //Azuriramo tokenovu mapu sa brojem poziva koje smo izvrsili
        token.getLnMap().put(AppConfig.myServentInfo, AppConfig.myServentInfo.getRnMap().get(AppConfig.myServentInfo));
        if(token.getQueue().peek() == null) token.getQueue().add(AppConfig.myServentInfo);
        token.getQueue().remove();

        SKTokenMessage skTokenMessage = new SKTokenMessage(AppConfig.myServentInfo,
                null, token.getLnMap(), token.getQueue());

        AppConfig.myServentInfo.getRnMap().forEach((key, value) -> {
            if(token.getLnMap().get(key) == null) {
                token.getLnMap().put(key, 0);
            }
            if(value == token.getLnMap().get(key) + 1) {
                token.getQueue().add(key);
            }
        });

        if(token.getQueue().peek() != null) {
            AppConfig.myServentInfo.setHasToken(false);
            for(int i = 0; i<AppConfig.myServentInfo.getNeighbors().size(); i++){
                int neighborId = AppConfig.myServentInfo.getNeighbors().get(i);

                MessageUtil.sendMessage(skTokenMessage.changeReceiver(neighborId).makeMeASender());
            }
        }


        distributedMutexInitiated.set(false);

    }

    public AtomicBoolean getDistributedMutexInitiated() {
        return distributedMutexInitiated;
    }
}
