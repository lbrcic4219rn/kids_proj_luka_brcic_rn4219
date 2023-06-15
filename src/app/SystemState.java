package app;

import java.io.Serializable;
import java.util.*;

public class SystemState implements Serializable { // SystemInfo ili SystemState - po uzoru na Chord state
    private List<ServantInfo> allNodeInfo = new ArrayList<>();
    private Map<Integer, ServantInfo> predecessorMap = new HashMap<>(); // key - node id, value - prethodnik tog node-a
    private Map<Integer, ServantInfo> successorMap = new HashMap<>(); // key - node id, value - sledbenik tog node-a
    private Map<Integer, List<String>> fileListMap = new HashMap<>();
    public ServantInfo createNewServantInfo(int servantId) {
        int portNumber = servantId + 1001; // Portovi za čvorove sistema će ići od 1001 do 2000 (port 1000 je rezervisan za bootstrap node).
        return  new ServantInfo("localhost", servantId, portNumber);
    }

    public ServantInfo getInfoById(int id) {
        for(ServantInfo info: allNodeInfo) {
            if(info.getId() == id) {
                return info;
            }
        }
        AppConfig.timestampedErrorPrint("Node with id " + id + " has not been found.");
        return null;
    }
    public ServantInfo getInfoOfServantOnPort(int port) {
        for(ServantInfo servantInfo: allNodeInfo) {
            if(servantInfo.getPort() == port) {
                return servantInfo;
            }
        }
        return null;
    }
    public int getServantCount() {
        return allNodeInfo.size();
    }
    public void addNewNodeInSystem(ServantInfo newNode) {
        if(allNodeInfo.size() < 4) {
            this.addNewNodeInSystemWhenThereIsLessThan4Nodes(newNode);
            return;
        }

        // Biramo random element iz liste, ispred koga cemo da ubacimo newNode
        Random random = new Random();
        int listSize = allNodeInfo.size();
        int randomIndex = random.nextInt(listSize);

        // Novi node se ubacuje jedno mesto desno od chosenNode-a.
        ServantInfo chosenNode = allNodeInfo.get(randomIndex);
        ServantInfo oldSuccessor = successorMap.get(chosenNode.getId());

        successorMap.put(chosenNode.getId(), newNode);
        predecessorMap.put(newNode.getId(), chosenNode);
        successorMap.put(newNode.getId(), oldSuccessor);
        predecessorMap.put(oldSuccessor.getId(), newNode);

        // Update-ovali smo prethodnika i sledbenika u mapama, sad treba da update-ujemo i listu komsija u allNodeInfo.
        // IDEJA: Ovde se "globalno" menja stanje sistema. Potom ce se poslati broadcast poruku da svaki cvor i kod
        // sebe "lokalno" treba da update-uje stanje sistema, tako sto ce povuci promene koje se njega ticu iz ovog globalnog stanja.
        // *Promene koje se njega ticu: njegov prethodnik, sledbenik, komsije.

        this.updateNeighboursInAllNodeInfoList(chosenNode, oldSuccessor, newNode);
    }

    private void updateNeighboursInAllNodeInfoList(ServantInfo chosenNode, ServantInfo oldSuccessor, ServantInfo newNode) {

        // Update-ovanje liste komsija radimo tako sto prvo izbrisemo iz allNodeInfo liste ServantInfo objekte koji
        // su se menjali, izmenimo ih i posle ih opet sacuvamo sa izmenjenim podacima. I na kraju svega dodamo newNode u listu.

        allNodeInfo.remove(chosenNode);
        allNodeInfo.remove(oldSuccessor);

        if(allNodeInfo.size() + 2 >= 5) {
            // 1. Ukidamo vezu izmedju chosenNode-a i sledbenika njegovog bivseg sledbenika.
            chosenNode.getNeighbours().remove(oldSuccessor.getSuccessor()); // ovaj remove je potencijalno problem; mozda cemo morati nesto preko id-a.
            oldSuccessor.getSuccessor().getNeighbours().remove(chosenNode); // ovaj remove je potencijalno problem; mozda cemo morati nesto preko id-a.

            // 2. Ukidamo vezu izmedju prethodnika od chosen node-a i bivseg sledbnika od chosen node-a.
            chosenNode.getPredecessor().getNeighbours().remove(oldSuccessor);
            oldSuccessor.getNeighbours().remove(chosenNode.getPredecessor());
        }

        // 3. Dodajemo vezu izmedju new node-a i chosen node-a.
        chosenNode.getNeighbours().add(newNode);
        newNode.getNeighbours().add(chosenNode);

        // 4. Dodajemo vezu izmedju new node-a i bivseg sledbenika od chosen node-a.
        newNode.getNeighbours().add(oldSuccessor);
        oldSuccessor.getNeighbours().add(newNode);

        // 5. Dodajemo vezu izmedju new node-a i prethodnika od chosen node-a.
        newNode.getNeighbours().add(chosenNode.getPredecessor());
        chosenNode.getPredecessor().getNeighbours().add(newNode);

        // 6. Dodajemo vezu izmedju new node-a i sledbenika od bivseg sledbenika od chosen node-a.
        newNode.getNeighbours().add(oldSuccessor.getSuccessor());
        oldSuccessor.getSuccessor().getNeighbours().add(newNode);


        // Update-ujemo i sledbenike i naslednike direktno u ovim objektima pre nego sto ih sacuvamo u listi.
        chosenNode.setSuccessor(newNode);
        newNode.setPredecessor(chosenNode);
        newNode.setSuccessor(oldSuccessor);
        oldSuccessor.setPredecessor(newNode);

        allNodeInfo.add(chosenNode);
        allNodeInfo.add(oldSuccessor);
        allNodeInfo.add(newNode);
    }

    private void addNewNodeInSystemWhenThereIsLessThan4Nodes(ServantInfo newNode) {
        if(allNodeInfo.size() == 0) { // Ako je sistem prazan (jos nema nijedan node u sistemu).
            successorMap.put(newNode.getId(), null);
            predecessorMap.put(newNode.getId(), null);
            allNodeInfo.add(newNode);
            return;
        }
        ServantInfo chosenNode = allNodeInfo.get(0);
        if(allNodeInfo.size() == 1) { // To znaci da nam je trenutno chosen node jedini node u sistemu i da ce ovaj koji dodajemo biti drugi.
            successorMap.put(chosenNode.getId(), newNode);
            predecessorMap.put(newNode.getId(), chosenNode);

            allNodeInfo.remove(chosenNode); // Brisemo outdated chosenNode info.

            chosenNode.setPredecessor(newNode);
            chosenNode.setSuccessor(newNode);
            newNode.setPredecessor(chosenNode);
            newNode.setSuccessor(chosenNode);

            chosenNode.addNeighbour(newNode); // Dodajemo new node u listu komsija chosen node-a.
            newNode.addNeighbour(chosenNode); // Dodajemo chosen node u listu komsija new node-a.

            allNodeInfo.add(chosenNode);
            allNodeInfo.add(newNode);
            return;
        }
        if(allNodeInfo.size() == 2) { // Ima ih 2, dodajemo treceg.
            successorMap.put(chosenNode.getId(),newNode);
            predecessorMap.put(chosenNode.getId(), chosenNode.getSuccessor());
            successorMap.put(newNode.getId(), chosenNode.getSuccessor());
            predecessorMap.put(newNode.getId(), chosenNode);
            successorMap.put(chosenNode.getSuccessor().getId(), chosenNode);
            predecessorMap.put(chosenNode.getSuccessor().getId(), newNode);

            ServantInfo oldSuccessor = chosenNode.getSuccessor();
            allNodeInfo.remove(chosenNode); // Brisemo outdated chosenNode info.
            allNodeInfo.remove(oldSuccessor); // Brisemo outdated oldSuccessor info.

            newNode.setSuccessor(oldSuccessor);
            newNode.setPredecessor(chosenNode);
            oldSuccessor.setSuccessor(chosenNode);
            oldSuccessor.setPredecessor(newNode);
            chosenNode.setSuccessor(newNode);
            chosenNode.setPredecessor(oldSuccessor);

            chosenNode.addNeighbour(newNode);
            oldSuccessor.addNeighbour(newNode);
            newNode.addNeighbour(chosenNode);
            newNode.addNeighbour(oldSuccessor);

            allNodeInfo.add(chosenNode);
            allNodeInfo.add(oldSuccessor);
            allNodeInfo.add(newNode);
            return;
        }
        if(allNodeInfo.size() == 3) {
            successorMap.put(chosenNode.getId(), newNode);
            predecessorMap.put(newNode.getId(), chosenNode);
            successorMap.put(newNode.getId(), chosenNode.getSuccessor());
            predecessorMap.put(chosenNode.getSuccessor().getId(), newNode);

            ServantInfo oldSuccessor = chosenNode.getSuccessor();
            ServantInfo predecessor = chosenNode.getPredecessor();
            allNodeInfo.clear();

            chosenNode.setSuccessor(newNode);
            newNode.setPredecessor(chosenNode);
            newNode.setSuccessor(oldSuccessor);
            oldSuccessor.setPredecessor(newNode);

            chosenNode.addNeighbour(newNode);
            oldSuccessor.addNeighbour(newNode);
            predecessor.addNeighbour(newNode);
            newNode.addNeighbour(chosenNode);
            newNode.addNeighbour(oldSuccessor);
            newNode.addNeighbour(predecessor);

            allNodeInfo.add(chosenNode);
            allNodeInfo.add(predecessor);
            allNodeInfo.add(oldSuccessor);
            allNodeInfo.add(newNode);
        }
    }

    public List<ServantInfo> getAllNodeInfo() {
        return allNodeInfo;
    }

    public Map<Integer, List<String>> getFileListMap() {
        return fileListMap;
    }

    public void setFileListMap(Map<Integer, List<String>> fileListMap) {
        this.fileListMap = fileListMap;
    }
}
