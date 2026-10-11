package app;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import servant.message.mutex_messages.TokenMessage;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class ServantInfo implements Receiver, Sender, Serializable {
    @Serial
    private static final long serialVersionUID = 5304170042791281555L;
    private final String ipAddress;
    private final int id;
    @Getter(AccessLevel.NONE)
    private final int listenerPort;
    private final List<ServantInfo> neighbours;

    // ---------------------------
    // Servant info can (and must) be instantiated without this info, later this info will be set.
    // Predecessor and successor are this node buddies.
    @Setter
    private ServantInfo predecessor;
    @Setter
    private ServantInfo successor;
    // ---------------------------
    // Suzuki-Kasami
    @Setter
    private AtomicInteger sm = new AtomicInteger(0);
    private Map<ServantInfo, Integer> rnMap;
    @Getter(AccessLevel.NONE)
    private TokenMessage tokenMessage;
    @Setter
    private boolean tokenOwner;
    @Setter
    private String rootDir;

    public ServantInfo(String ipAddress, int id, int listenerPort, List<ServantInfo> neighbours) {
        this.ipAddress = ipAddress;
        this.id = id;
        this.listenerPort = listenerPort;
        this.neighbours = neighbours;
        this.initSuzukiKasamiAttributes();
    }

    public ServantInfo(String ipAddress, int id, int listenerPort) {
        this.ipAddress = ipAddress;
        this.id = id;
        this.listenerPort = listenerPort;
        this.neighbours = new ArrayList<>();
        this.initSuzukiKasamiAttributes();
    }

    public ServantInfo(String ipAddress, int listenerPort) {
        this.ipAddress = ipAddress;
        this.id = -1; // to be set later
        this.listenerPort = listenerPort;
        this.neighbours = new ArrayList<>();
        this.initSuzukiKasamiAttributes();
    }

    private void initSuzukiKasamiAttributes() {
        this.tokenOwner = false;
        this.tokenMessage = null;
        this.rnMap = new ConcurrentHashMap<>();
        rnMap.put(this, 0);
    }

    @Override
    public int getPort() {
        return this.listenerPort;
    }

    public TokenMessage getToken() {
        return tokenMessage;
    }

    public void setToken(TokenMessage tokenMessage) {
        this.tokenMessage = tokenMessage;
    }

    public void addNeighbour(ServantInfo neighbour) {
        neighbours.add(neighbour);
    }

    @Override
    public String toString() {
        String tmPrint = "null";
        if (tokenMessage != null) {
            tmPrint = "exists";
        }
        return "[ id: " + id + " | ipAdress: " + ipAddress + " | port: " + listenerPort + " | isTokenOwner: " + tokenOwner + "| tokenMessage: " + tmPrint + "]";
    }
}
