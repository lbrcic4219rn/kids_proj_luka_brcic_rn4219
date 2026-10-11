package failure;

import app.AppConfig;
import app.Cancellable;
import app.MembershipManager;
import app.ServantInfo;
import app.SystemState;
import servant.message.Message;
import servant.message.MessageType;
import servant.message.Payloads.NodeRemoval;
import servant.message.Payloads.StateDigest;
import servant.message.Payloads.StateSync;
import servant.message.util.MessageUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class FailureDetector implements Runnable, Cancellable {
    private static final long SYNC_PAUSE_MS = 2000;
    private static final String NODE = "Node ";

    private final Map<ServantInfo, Long> lastSeen = new ConcurrentHashMap<>();
    private final Set<ServantInfo> suspected = ConcurrentHashMap.newKeySet();
    private final Set<ServantInfo> confirmed = ConcurrentHashMap.newKeySet();
    private final Map<ServantInfo, Long> lastSync = new ConcurrentHashMap<>();
    private volatile boolean working = true;

    @SuppressWarnings("BusyWait")
    @Override
    public void run() {
        long period = Math.max(100, AppConfig.weakLimit() / 3);
        while (working) {
            try {
                checkNeighbours();
                Thread.sleep(period);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                AppConfig.timestampedErrorPrint("Error in failure detector", e);
            }
        }
    }

    private void checkNeighbours() {
        List<ServantInfo> neighbours = AppConfig.state().getNeighbours();
        lastSeen.keySet().retainAll(neighbours);
        suspected.retainAll(neighbours);
        confirmed.retainAll(neighbours);
        long now = System.currentTimeMillis();
        for (ServantInfo neighbour : neighbours) {
            lastSeen.putIfAbsent(neighbour, now);
            MessageUtil.sendDirect(MessageType.IS_OK, neighbour, digest());
            long silentFor = now - lastSeen.get(neighbour);
            if (silentFor <= AppConfig.weakLimit()) {
                if (suspected.remove(neighbour)) {
                    AppConfig.timestampedStandardPrint(NODE + neighbour + " responds again, no longer suspicious.");
                }
                confirmed.remove(neighbour);
            } else if (silentFor > AppConfig.strongLimit() && confirmed.contains(neighbour)) {
                AppConfig.timestampedErrorPrint(NODE + neighbour + " passed the strong limit (" + silentFor + " ms).");
                MembershipManager.nodeRemoved(neighbour, false, true);
            } else if (suspected.add(neighbour)) {
                AppConfig.timestampedErrorPrint(NODE + neighbour + " passed the weak limit (" + silentFor
                        + " ms) and is now SUSPICIOUS.");
                CompletableFuture.runAsync(() -> askForConfirmation(neighbour));
            }
        }
    }

    private void askForConfirmation(ServantInfo suspect) {
        List<ServantInfo> helpers = new ArrayList<>(AppConfig.state().getNeighbours());
        helpers.remove(suspect);
        helpers.removeAll(suspected);
        if (helpers.isEmpty()) {
            AppConfig.timestampedStandardPrint("No other node can check " + suspect + ", suspicion stands.");
            confirmed.add(suspect);
            return;
        }
        ServantInfo helper = helpers.get(ThreadLocalRandom.current().nextInt(helpers.size()));
        Message reply = MessageUtil.request(MessageType.CHECK_SUSPECT, helper, suspect, AppConfig.weakLimit() * 2 + 3000);
        if (reply != null && Boolean.TRUE.equals(reply.payload())) {
            AppConfig.timestampedErrorPrint(NODE + helper + " CONFIRMED that " + suspect + " does not respond.");
            confirmed.add(suspect);
            return;
        }
        AppConfig.timestampedStandardPrint(NODE + helper + " did not confirm that " + suspect + " is down.");
        try {
            Thread.sleep(AppConfig.weakLimit());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        suspected.remove(suspect);
    }

    public void onCheckSuspect(Message request) {
        ServantInfo suspect = request.payload();
        Message pong = MessageUtil.requestDirect(MessageType.IS_OK, suspect, digest(), AppConfig.weakLimit());
        MessageUtil.reply(request, MessageType.SUSPECT_RESULT, pong == null);
    }

    public void onPing(Message ping) {
        if (AppConfig.state().isRemoved(ping.getSender())) {
            MessageUtil.sendDirect(MessageType.NODE_REMOVAL, ping.getSender(), new NodeRemoval(ping.getSender(), false));
            return;
        }
        MessageUtil.reply(ping, MessageType.OK, digest());
        compareState(ping);
    }

    public void onPong(Message pong) {
        compareState(pong);
    }

    private void compareState(Message message) {
        if (!AppConfig.isJoined() || AppConfig.isQuitting() || digest().equals(message.payload())) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastSync.get(message.getSender());
        if (previous != null && now - previous < SYNC_PAUSE_MS) {
            return;
        }
        lastSync.put(message.getSender(), now);
        SystemState state = AppConfig.state();
        MessageUtil.sendDirect(MessageType.UPDATE_SYSTEM, message.getSender(), new StateSync(
                new ArrayList<>(state.getMembers()), new HashSet<>(state.getRemoved()), AppConfig.index().copy()));
    }

    private StateDigest digest() {
        return new StateDigest(AppConfig.state().digest(), AppConfig.index().digest());
    }

    public void heard(ServantInfo node) {
        lastSeen.computeIfPresent(node, (key, value) -> System.currentTimeMillis());
    }

    public void forget(ServantInfo node) {
        lastSeen.remove(node);
        suspected.remove(node);
        confirmed.remove(node);
        lastSync.remove(node);
    }

    @Override
    public void stop() {
        this.working = false;
    }
}
