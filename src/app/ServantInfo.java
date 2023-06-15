package app;

import servant.message.mutex_messages.TokenMessage;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServantInfo implements Receiver, Sender, Serializable {
    private static final long serialVersionUID = 5304170042791281555L;
    private final String ipAddress;
    private final int id;
    private final int listenerPort;
    private final List<ServantInfo> neighbours;

    // ---------------------------
    // Servant info can (and must) be instantiated without this info, later this info will be set.
    // Predecessor and successor are this node buddies.
    private ServantInfo predecessor;
    private ServantInfo successor;
    // ---------------------------
    // Suzuki-Kasami
    private AtomicInteger sm = new AtomicInteger(0);
    private Map<ServantInfo, Integer> rnMap;
    private TokenMessage tokenMessage;
    private boolean tokenOwner;
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
    public String getIpAddress() {
        return this.ipAddress;
    }
    @Override
    public int getPort() {
        return this.listenerPort;
    }
    public int getId() {
        return this.id;
    }
    public List<ServantInfo> getNeighbours() {
        return neighbours;
    }

    public ServantInfo getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(ServantInfo predecessor) {
        this.predecessor = predecessor;
    }

    public ServantInfo getSuccessor() {
        return successor;
    }

    public void setSuccessor(ServantInfo successor) {
        this.successor = successor;
    }

    public AtomicInteger getSm() {
        return sm;
    }

    public void setSm(AtomicInteger sm) {
        this.sm = sm;
    }

    public Map<ServantInfo, Integer> getRnMap() {
        return rnMap;
    }

    public TokenMessage getToken() {
        return tokenMessage;
    }

    public void setToken(TokenMessage tokenMessage) {
        this.tokenMessage = tokenMessage;
    }

    public boolean isTokenOwner() {
        return tokenOwner;
    }

    public void setTokenOwner(boolean tokenOwner) {
        this.tokenOwner = tokenOwner;
    }

    public void addNeighbour(ServantInfo neighbour) {
        neighbours.add(neighbour);
    }

    @Override
    public String toString() {
        String tmPrint = "null";
        if(tokenMessage != null) {
            tmPrint = "exists";
        }
        return "[ id: " + id + " | ipAdress: " + ipAddress + " | port: " + listenerPort + " | isTokenOwner: " + tokenOwner + "| tokenMessage: " + tmPrint +  "]";
    }

    public String getRootDir() {
        return rootDir;
    }

    public void setRootDir(String rootDir) {
        this.rootDir = rootDir;
    }
}
