package app;

import servant.message.Message;
import servant.message.MessageType;
import servant.message.Payloads.NodeRemoval;
import servant.message.Payloads.StateSync;
import servant.message.Payloads.Welcome;
import servant.message.util.MessageUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class MembershipManager {
    private MembershipManager() {
    }

    public static void admit(Message request) {
        ServantInfo newNode = request.getSender();
        AppConfig.mutex().lock();
        try {
            nodeAdded(newNode, true);
            SystemState state = AppConfig.state();
            Welcome welcome = new Welcome(new ArrayList<>(state.getMembers()), new HashSet<>(state.getRemoved()),
                    AppConfig.mutex().heldIndex().copy(), AppConfig.mutex().getEpoch());
            MessageUtil.reply(request, MessageType.SYSTEM_NODE_SAYS_WELCOME, welcome);
        } finally {
            AppConfig.mutex().unlock();
        }
    }

    public static synchronized void nodeAdded(ServantInfo node, boolean announce) {
        SystemState state = AppConfig.state();
        ServantInfo previousRun = state.olderIncarnationOf(node);
        if (previousRun != null) {
            nodeRemoved(previousRun, false, announce);
        }
        List<ServantInfo> before = state.getMembers();
        if (!state.addNode(node)) {
            return;
        }
        AppConfig.timestampedStandardPrint("Node " + node + " joined. Nodes in system: " + state.getMembers().size());
        if (announce) {
            MessageUtil.broadcast(MessageType.NEW_NODE_ARRIVAL, node);
        }
        AppConfig.files().rebalance(before, state.getMembers());
    }

    public static synchronized void nodeRemoved(ServantInfo node, boolean graceful, boolean announce) {
        if (node.equals(AppConfig.me())) {
            removedFromSystem();
            return;
        }
        SystemState state = AppConfig.state();
        List<ServantInfo> before = state.getMembers();
        if (!state.removeNode(node)) {
            return;
        }
        AppConfig.timestampedStandardPrint("Node " + node + (graceful ? " left the system." : " FAILED and is removed.")
                + " Nodes in system: " + state.getMembers().size());
        if (announce) {
            MessageUtil.broadcast(MessageType.NODE_REMOVAL, new NodeRemoval(node, graceful));
            MessageUtil.sendDirect(MessageType.LEFT_SYSTEM, AppConfig.bootstrap(), node);
        }
        AppConfig.mutex().nodeRemoved(node);
        AppConfig.detector().forget(node);
        AppConfig.files().rebalance(before, state.getMembers());
        if (!graceful && isCoordinator()) {
            AppConfig.mutex().recoverToken();
        }
    }

    public static synchronized void applySync(StateSync sync) {
        for (ServantInfo node : sync.removed()) {
            if (!AppConfig.state().isRemoved(node)) {
                nodeRemoved(node, false, false);
            }
        }
        for (ServantInfo node : sync.members()) {
            nodeAdded(node, false);
        }
        AppConfig.files().mergeIndex(sync.index());
    }

    public static synchronized void applyWelcome(Welcome welcome) {
        SystemState state = AppConfig.state();
        welcome.removed().forEach(state::removeNode);
        welcome.members().forEach(state::addNode);
        state.addNode(AppConfig.me());
        AppConfig.files().mergeIndex(welcome.index());
        AppConfig.mutex().adoptEpoch(welcome.epoch());
    }

    public static void leave() {
        AppConfig.setQuitting(true);
        AppConfig.mutex().lock();
        synchronized (MembershipManager.class) {
            SystemState state = AppConfig.state();
            List<ServantInfo> before = state.getMembers();
            MessageUtil.broadcast(MessageType.NODE_REMOVAL, new NodeRemoval(AppConfig.me(), true));
            MessageUtil.sendDirect(MessageType.LEFT_SYSTEM, AppConfig.bootstrap(), AppConfig.me());
            state.removeNode(AppConfig.me());
            AppConfig.files().rebalance(before, state.getMembers());
        }
        AppConfig.mutex().handOff();
    }

    private static boolean isCoordinator() {
        List<ServantInfo> members = AppConfig.state().getMembers();
        return !members.isEmpty() && members.get(0).equals(AppConfig.me());
    }

    private static void removedFromSystem() {
        if (AppConfig.isQuitting()) {
            return;
        }
        AppConfig.timestampedErrorPrint("The system declared this node dead. Shutting down.");
        System.exit(2);
    }
}
