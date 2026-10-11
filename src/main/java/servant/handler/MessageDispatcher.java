package servant.handler;

import app.AppConfig;
import app.MembershipManager;
import app.ServantInfo;
import servant.message.Message;
import servant.message.Payloads.NodeRemoval;
import servant.message.util.MessageUtil;

public final class MessageDispatcher {
    private MessageDispatcher() {
    }

    public static void onReceive(Message message) {
        try {
            ServantInfo me = AppConfig.me();
            AppConfig.detector().heard(message.getLastHop());
            if (message.isBroadcast()) {
                if (AppConfig.isJoined() && MessageUtil.markSeen(message)) {
                    log(message);
                    handle(message);
                    MessageUtil.rebroadcast(message);
                }
            } else if (message.getReceiver().equals(me)) {
                deliver(message);
            } else if (message.getReceiver().getKey().equals(me.getKey())) {
                AppConfig.timestampedErrorPrint("Dropping message for a previous run of this node: " + message);
            } else {
                MessageUtil.route(message);
            }
        } catch (RuntimeException e) {
            AppConfig.timestampedErrorPrint("Error while handling " + message, e);
        }
    }

    public static void deliver(Message message) {
        log(message);
        MessageUtil.completeReply(message);
        handle(message);
    }

    private static void log(Message message) {
        if (message.getType().isLogged()) {
            AppConfig.timestampedStandardPrint("Got " + message);
        }
    }

    private static void handle(Message message) {
        switch (message.getType()) {
            case IS_OK -> AppConfig.detector().onPing(message);
            case OK -> AppConfig.detector().onPong(message);
            case CHECK_SUSPECT -> AppConfig.detector().onCheckSuspect(message);
            case REPLICATE_FILES -> AppConfig.files().onReplicate(message.payload());
            default -> handleAsMember(message);
        }
    }

    private static void handleAsMember(Message message) {
        if (!AppConfig.isJoined()) {
            return;
        }
        switch (message.getType()) {
            case SAY_HI_TO_SYSTEM_NODE -> MembershipManager.admit(message);
            case NEW_NODE_ARRIVAL -> MembershipManager.nodeAdded(message.payload(), false);
            case NODE_REMOVAL -> {
                NodeRemoval removal = message.payload();
                MembershipManager.nodeRemoved(removal.node(), removal.graceful(), false);
            }
            case UPDATE_SYSTEM -> MembershipManager.applySync(message.payload());
            case ASK_FOR_TOKEN -> AppConfig.mutex().onRequest(message.payload());
            case TOKEN -> AppConfig.mutex().onToken(message.payload());
            case TOKEN_QUERY -> AppConfig.mutex().onTokenQuery(message.payload(), message.getSender());
            case TOKEN_HAVE -> AppConfig.mutex().onTokenHave(message.payload());
            case TOKEN_REGENERATED -> AppConfig.mutex().onTokenRegenerated(message.payload());
            case ADD_FILE -> AppConfig.files().onStore(message);
            case REQUEST_FILE -> AppConfig.files().onFileRequest(message);
            case UPDATE_INDEX -> AppConfig.files().onIndexUpdate(message.payload());
            default -> {
                // Replies (FILE_STORED, GIVE_FILE, SUSPECT_RESULT...) were already handed to whoever is waiting for them.
            }
        }
    }
}
