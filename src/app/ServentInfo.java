package app;

import servent.message.mutex.SKTokenMessage;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is an immutable class that holds all the information for a servent.
 *
 * @author bmilojkovic
 */
public class ServentInfo implements Serializable {

	private static final long serialVersionUID = 5304170042791281555L;
	private final int id;
	private final String ipAddress;
	private final int listenerPort;
	private final List<Integer> neighbors;

	// Suzuki kasami
	private AtomicInteger sm = new AtomicInteger(0);
	private Map<ServentInfo, Integer> rnMap;
	private SKTokenMessage token;
	private boolean hasToken;

	public ServentInfo(String ipAddress, int id, int listenerPort, List<Integer> neighbors) {
		this.ipAddress = ipAddress;
		this.listenerPort = listenerPort;
		this.id = id;
		this.neighbors = neighbors;

		//Suzuki Kasami
		this.hasToken = false;
		this.token = null;
		this.rnMap = new ConcurrentHashMap<>();
		rnMap.put(this, 0);
	}

	public Map<ServentInfo, Integer> getRnMap() {
		return rnMap;
	}

	public void setRnMap(Map<ServentInfo, Integer> rnMap) {
		this.rnMap = rnMap;
	}

	public SKTokenMessage getToken() {
		return token;
	}

	public void setToken(SKTokenMessage token) {
		this.token = token;
	}

	public boolean isHasToken() {
		return hasToken;
	}

	public void setHasToken(boolean hasToken) {
		this.hasToken = hasToken;
	}

	public AtomicInteger getSm() {
		return sm;
	}

	public void setSm(AtomicInteger sm) {
		this.sm = sm;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public int getListenerPort() {
		return listenerPort;
	}

	public int getId() {
		return id;
	}
	
	public List<Integer> getNeighbors() {
		return neighbors;
	}
	
	@Override
	public String toString() {
		return "[" + id + "|" + ipAddress + "|" + listenerPort + "]";
	}
}
