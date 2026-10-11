package servant.message.util;

import app.AppConfig;
import app.ServantInfo;
import servant.message.Message;
import servant.message.MessageType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class MessageUtil {
    private static final int MIN_DELAY_MS = 20;
    private static final int MAX_DELAY_MS = 150;
    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int MAX_HOPS = 32;
    private static final int MAX_REMEMBERED_BROADCASTS = 100_000;

    private static final ExecutorService SENDERS = Executors.newCachedThreadPool();
    private static final AtomicLong COUNTER = new AtomicLong();
    private static final Map<String, CompletableFuture<Message>> PENDING = new ConcurrentHashMap<>();
    private static final Set<String> SEEN_BROADCASTS = ConcurrentHashMap.newKeySet();
    private static final AtomicReference<Consumer<Message>> LOCAL_DELIVERY = new AtomicReference<>(message -> {
    });

    private MessageUtil() {
    }

    public static void setLocalDelivery(Consumer<Message> delivery) {
        LOCAL_DELIVERY.set(delivery);
    }

    private static void submit(Runnable task) {
        try {
            SENDERS.submit(task);
        } catch (RejectedExecutionException e) {
            // The node is shutting down, new messages are no longer sent.
        }
    }

    private static Message create(MessageType type, ServantInfo receiver, Serializable payload, String replyTo, boolean direct) {
        ServantInfo me = AppConfig.me();
        return new Message(me.getId() + "/" + COUNTER.incrementAndGet(), type, me, receiver, me, 0, replyTo, direct, payload);
    }

    public static void send(MessageType type, ServantInfo receiver, Serializable payload) {
        dispatch(create(type, receiver, payload, null, false));
    }

    public static void sendDirect(MessageType type, ServantInfo receiver, Serializable payload) {
        dispatch(create(type, receiver, payload, null, true));
    }

    public static void reply(Message request, MessageType type, Serializable payload) {
        dispatch(create(type, request.getSender(), payload, request.getId(), request.isDirect()));
    }

    public static void broadcast(MessageType type, Serializable payload) {
        Message message = create(type, null, payload, null, false);
        SEEN_BROADCASTS.add(message.getId());
        for (ServantInfo neighbour : AppConfig.state().getNeighbours()) {
            submit(() -> transmitDelayed(message, neighbour));
        }
    }

    public static void rebroadcast(Message message) {
        Message forwarded = message.forwardedBy(AppConfig.me());
        for (ServantInfo neighbour : AppConfig.state().getNeighbours()) {
            if (!neighbour.equals(message.getLastHop()) && !neighbour.equals(message.getSender())) {
                submit(() -> transmitDelayed(forwarded, neighbour));
            }
        }
    }

    public static boolean markSeen(Message message) {
        if (SEEN_BROADCASTS.size() > MAX_REMEMBERED_BROADCASTS) {
            SEEN_BROADCASTS.clear();
        }
        return SEEN_BROADCASTS.add(message.getId());
    }

    public static CompletableFuture<Message> requestAsync(MessageType type, ServantInfo receiver, Serializable payload,
                                                          long timeoutMs, boolean direct) {
        Message message = create(type, receiver, payload, null, direct);
        CompletableFuture<Message> future = new CompletableFuture<>();
        PENDING.put(message.getId(), future);
        dispatch(message);
        return future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .whenComplete((reply, error) -> PENDING.remove(message.getId()));
    }

    public static Message request(MessageType type, ServantInfo receiver, Serializable payload, long timeoutMs) {
        return await(requestAsync(type, receiver, payload, timeoutMs, false));
    }

    public static Message requestDirect(MessageType type, ServantInfo receiver, Serializable payload, long timeoutMs) {
        return await(requestAsync(type, receiver, payload, timeoutMs, true));
    }

    public static Message await(CompletableFuture<Message> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public static void completeReply(Message message) {
        if (message.getReplyTo() != null) {
            CompletableFuture<Message> future = PENDING.get(message.getReplyTo());
            if (future != null) {
                future.complete(message);
            }
        }
    }

    private static void dispatch(Message message) {
        if (message.getReceiver().equals(AppConfig.me())) {
            submit(() -> LOCAL_DELIVERY.get().accept(message));
        } else {
            route(message);
        }
    }

    public static void route(Message message) {
        submit(() -> {
            if (message.getHops() >= MAX_HOPS) {
                undeliverable(message, "too many hops");
                return;
            }
            delay();
            Message forwarded = message.forwardedBy(AppConfig.me());
            Set<ServantInfo> unreachable = new HashSet<>();
            while (true) {
                ServantInfo hop = nextHop(message, unreachable);
                if (hop == null) {
                    undeliverable(message, "no reachable next hop");
                    return;
                }
                if (transmit(forwarded, hop)) {
                    return;
                }
                unreachable.add(hop);
            }
        });
    }

    private static ServantInfo nextHop(Message message, Set<ServantInfo> unreachable) {
        ServantInfo receiver = message.getReceiver();
        if (message.isDirect() || AppConfig.state() == null) {
            return unreachable.contains(receiver) ? null : receiver;
        }
        return AppConfig.state().nextHop(receiver, unreachable);
    }

    private static void undeliverable(Message message, String reason) {
        if (message.getType().isLogged()) {
            AppConfig.timestampedErrorPrint("Couldn't deliver " + message + ": " + reason);
        }
        CompletableFuture<Message> future = PENDING.get(message.getId());
        if (future != null) {
            future.completeExceptionally(new IOException(reason));
        }
    }

    private static void transmitDelayed(Message message, ServantInfo hop) {
        delay();
        transmit(message, hop);
    }

    private static void delay() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(MIN_DELAY_MS, MAX_DELAY_MS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean transmit(Message message, ServantInfo hop) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hop.getIpAddress(), hop.getPort()), CONNECT_TIMEOUT_MS);
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            output.writeObject(message);
            output.flush();
            if (message.getType().isLogged()) {
                AppConfig.timestampedStandardPrint("Sent " + message + " via " + hop.getKey());
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static Message readMessage(Socket socket) {
        try (socket) {
            ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
            return (Message) input.readObject();
        } catch (IOException | ClassNotFoundException e) {
            AppConfig.timestampedErrorPrint("Error while reading message: " + e);
            return null;
        }
    }

    public static void shutdown() {
        SENDERS.shutdown();
        try {
            if (!SENDERS.awaitTermination(5, TimeUnit.SECONDS)) {
                SENDERS.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
