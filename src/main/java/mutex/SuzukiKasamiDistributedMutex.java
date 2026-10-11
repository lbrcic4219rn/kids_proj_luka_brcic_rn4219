package mutex;

import app.AppConfig;
import app.ServantInfo;
import file.FileIndex;
import servant.message.MessageType;
import servant.message.Payloads.TokenRequest;
import servant.message.util.MessageUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class SuzukiKasamiDistributedMutex implements DistributedMutex {
    private static final long RETRY_MS = 3000;
    private static final long RECOVERY_WAIT_MS = 4000;
    private static final long RECOVERY_AFTER_MS = 15000;
    private static final long STAMP_JUMP = 1_000_000;
    private static final int EPOCH_NODE_BITS = 20;

    private final Semaphore localGate = new Semaphore(1, true);
    private final Map<String, Integer> rn = new HashMap<>();
    private final Set<Long> answeredQueries = ConcurrentHashMap.newKeySet();
    private final ExecutorService recoveryExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "token-recovery");
        thread.setDaemon(true);
        return thread;
    });
    private Token token;
    private boolean wanting;
    private boolean inCriticalSection;
    private long epoch;

    public synchronized void createInitialToken() {
        epoch = nextEpoch();
        token = new Token(epoch, 0, new FileIndex());
    }

    @Override
    public void lock() {
        localGate.acquireUninterruptibly();
        long waitingSince = System.currentTimeMillis();
        while (true) {
            int sequenceNumber;
            synchronized (this) {
                if (inCriticalSection) {
                    return;
                }
                if (token != null) {
                    wanting = false;
                    inCriticalSection = true;
                    return;
                }
                wanting = true;
                sequenceNumber = rn.merge(AppConfig.me().getId(), 1, Integer::sum);
            }
            MessageUtil.broadcast(MessageType.ASK_FOR_TOKEN, new TokenRequest(AppConfig.me(), sequenceNumber));
            if (awaitToken()) {
                return;
            }
            if (System.currentTimeMillis() - waitingSince > RECOVERY_AFTER_MS) {
                waitingSince = System.currentTimeMillis();
                recoverToken();
            }
        }
    }

    private synchronized boolean awaitToken() {
        long deadline = System.currentTimeMillis() + RETRY_MS;
        try {
            while (!inCriticalSection) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                wait(left);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            wanting = false;
            localGate.release();
            throw new IllegalStateException("Interrupted while waiting for token.", e);
        }
    }

    @Override
    public void unlock() {
        synchronized (this) {
            if (!inCriticalSection) {
                return;
            }
            inCriticalSection = false;
            if (token != null) {
                token.getLn().put(AppConfig.me().getId(), rn.getOrDefault(AppConfig.me().getId(), 0));
                passTokenIfRequested();
            }
        }
        localGate.release();
    }

    public synchronized void handOff() {
        inCriticalSection = false;
        wanting = false;
        if (token == null) {
            return;
        }
        token.getLn().put(AppConfig.me().getId(), rn.getOrDefault(AppConfig.me().getId(), 0));
        collectOutstandingRequests();
        if (token.getQueue().isEmpty()) {
            AppConfig.state().getMembers().stream().findFirst().ifPresent(token.getQueue()::add);
        }
        passToken();
    }

    private void passTokenIfRequested() {
        collectOutstandingRequests();
        passToken();
    }

    private void collectOutstandingRequests() {
        List<ServantInfo> ring = AppConfig.state().getMembers();
        int start = ring.indexOf(AppConfig.me());
        for (int i = 1; i <= ring.size(); i++) {
            ServantInfo node = ring.get((start + i) % ring.size());
            boolean outstanding = rn.getOrDefault(node.getId(), 0) > token.getLn().getOrDefault(node.getId(), 0);
            if (outstanding && !node.equals(AppConfig.me()) && !token.getQueue().contains(node)) {
                token.getQueue().add(node);
            }
        }
    }

    private void passToken() {
        while (!token.getQueue().isEmpty()) {
            ServantInfo next = token.getQueue().poll();
            if (next.equals(AppConfig.me()) || !AppConfig.state().isMember(next)) {
                continue;
            }
            Token outgoing = token;
            token = null;
            AppConfig.timestampedStandardPrint("Passing TOKEN to " + next);
            MessageUtil.send(MessageType.TOKEN, next, outgoing);
            return;
        }
    }

    public synchronized void onRequest(TokenRequest request) {
        rn.merge(request.node().getId(), request.sequenceNumber(), Math::max);
        if (token != null && !inCriticalSection) {
            passTokenIfRequested();
        }
    }

    public synchronized void onToken(Token received) {
        if (received.getEpoch() < epoch) {
            AppConfig.timestampedErrorPrint("Dropping TOKEN from an older epoch.");
            return;
        }
        epoch = received.getEpoch();
        token = received;
        token.getIndex().merge(AppConfig.index());
        AppConfig.files().mergeIndex(token.getIndex());
        token.getQueue().remove(AppConfig.me());
        if (AppConfig.isQuitting() && !inCriticalSection && !wanting) {
            handOff();
            return;
        }
        if (wanting) {
            wanting = false;
            inCriticalSection = true;
            AppConfig.timestampedStandardPrint("I received the TOKEN I requested.");
            notifyAll();
        } else {
            token.getLn().put(AppConfig.me().getId(), rn.getOrDefault(AppConfig.me().getId(), 0));
            passTokenIfRequested();
        }
    }

    public synchronized void nodeRemoved(ServantInfo node) {
        rn.remove(node.getId());
        if (token != null) {
            token.getLn().remove(node.getId());
            token.getQueue().remove(node);
        }
    }

    public void recoverToken() {
        recoveryExecutor.submit(() -> {
            try {
                runRecovery();
            } catch (RuntimeException e) {
                AppConfig.timestampedErrorPrint("Error while recovering token", e);
            }
        });
    }

    private void runRecovery() {
        long query;
        synchronized (this) {
            if (token != null) {
                return;
            }
            query = nextEpoch();
            epoch = query;
        }
        AppConfig.timestampedStandardPrint("Checking whether the TOKEN still exists (epoch " + query + ").");
        MessageUtil.broadcast(MessageType.TOKEN_QUERY, query);
        try {
            Thread.sleep(RECOVERY_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        synchronized (this) {
            if (token != null || epoch != query || answeredQueries.remove(query)) {
                return;
            }
            epoch = nextEpoch();
            FileIndex index = AppConfig.index().copy();
            token = new Token(epoch, index.maxStamp() + STAMP_JUMP, index);
            token.getLn().putAll(rn);
            AppConfig.timestampedErrorPrint("Nobody has the TOKEN. REGENERATED it with epoch " + epoch + ".");
            MessageUtil.broadcast(MessageType.TOKEN_REGENERATED, epoch);
            if (wanting) {
                wanting = false;
                inCriticalSection = true;
                notifyAll();
            }
        }
    }

    public synchronized void onTokenQuery(long query, ServantInfo coordinator) {
        epoch = Math.max(epoch, query);
        if (token != null) {
            token.setEpoch(epoch);
            MessageUtil.send(MessageType.TOKEN_HAVE, coordinator, query);
        }
    }

    public void onTokenHave(long query) {
        answeredQueries.add(query);
    }

    public synchronized void onTokenRegenerated(long newEpoch) {
        if (newEpoch <= epoch) {
            return;
        }
        epoch = newEpoch;
        if (token != null && token.getEpoch() < newEpoch) {
            AppConfig.timestampedErrorPrint("Discarding my TOKEN, a newer one was generated.");
            token = null;
        }
    }

    public synchronized void adoptEpoch(long knownEpoch) {
        epoch = Math.max(epoch, knownEpoch);
    }

    public synchronized long getEpoch() {
        return epoch;
    }

    public synchronized FileIndex heldIndex() {
        return token != null ? token.getIndex() : AppConfig.index();
    }

    public synchronized long nextStamp() {
        return token != null ? token.nextStamp() : AppConfig.index().maxStamp() + 1;
    }

    public synchronized String describe() {
        return (token != null ? "token: HERE" : "token: elsewhere") + ", epoch: " + epoch
                + (inCriticalSection ? ", in critical section" : "");
    }

    private long nextEpoch() {
        return (((epoch >> EPOCH_NODE_BITS) + 1) << EPOCH_NODE_BITS) | (AppConfig.me().getPort() & ((1 << EPOCH_NODE_BITS) - 1));
    }
}
