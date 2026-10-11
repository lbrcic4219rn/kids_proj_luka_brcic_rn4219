# SillyGit: system and protocol documentation

## 1. Running

| What | Command |
|---|---|
| Build and run the tests | `./gradlew build` |
| Bootstrap server | `java -cp build/classes/java/main app.BootstrapServer [port] [ip]` (defaults: 1000, localhost) |
| Node | `java -cp build/classes/java/main app.ServantMain config/servent0.properties` |
| Same through Gradle | `./gradlew runBootstrap` and `./gradlew run --args="config/servent0.properties"` |

The `config` directory holds six configurations (servent0 to servent5, teams a and b) for running on one machine.
Each node runs in its own terminal. Commands are typed on the standard input of the node.

## 2. Node configuration

The configuration file (properties format) is given as the argument at startup.

| Attribute | Meaning |
|---|---|
| `work_root` | Work root: the files the user works with |
| `storage` | Storage: the files this node is responsible for (the user does not touch them) |
| `ip`, `port` | Address the node listens on |
| `bootstrap_ip`, `bootstrap_port` | Address of the bootstrap server |
| `weak_limit` | Weak failure limit in ms (for example 1000) |
| `strong_limit` | Strong failure limit in ms (for example 10000) |
| `team` | Team the node belongs to, lowercase letters a to z |
| `limit` | Limit that triggers the team optimisation |

## 3. Commands

| Command | Effect |
|---|---|
| `add name` | Adds a file or (recursively) a directory from the work root. The file gets version 0. Error if it already exists. |
| `pull name [version]` | Fetches a file or a directory. Without a version the latest one is fetched. |
| `commit name` | Writes a modified file or directory to the system and increases the version. An unmodified file keeps its version. |
| `remove name` | Removes a file or a directory from the system. |
| `info` | Prints the members of the system, the neighbours, the token state, the index, the storage and the work root. |
| `quit` (or `stop`) | Orderly shutdown of the node. |

Names are paths relative to the work root. A single dot means the whole work root.

**Conflict.** If on commit the file in the system is already at a higher version than the one the user started from,
the node asks for one of:

| Choice | Effect |
|---|---|
| `view` | Fetches the version from the system under the name `<name>.conflict`, prints it and asks again |
| `push` | Overwrites the file in the system with the local file (a new version) |
| `pull` | Overwrites the local file with the file from the system |

## 4. Architecture

The bootstrap server is not a node. It gives a new node the address of one randomly chosen node of the system and
nothing more. It knows nothing about the architecture; it only remembers the addresses of nodes that reported that
they joined or left.

A node is identified by its address, its port and the moment it was started. Every node knows the set of members of
the system (and the set of nodes that left it). From that set all nodes compute the same things, deterministically:

* **Ring.** Members sorted by (team, address, port). Members of the same team are therefore next to each other in the ring.
* **Neighbours.** The nodes at distance 1, 2, 4, 8 and so on, in both directions along the ring. The number of
  neighbours is logarithmic. A node talks directly only to its neighbours (exceptions: the bootstrap, a node that
  is just joining, and the check of a suspicious node).
* **Forwarding.** A message for a node that is not a neighbour is sent to the neighbour that is closest to the
  destination in the ring. The remaining distance is at least halved on every hop, so the number of hops is
  logarithmic. If the next hop is unreachable, another neighbour is tried.
* **Broadcast.** A node that receives a broadcast message for the first time handles it and forwards it to all its neighbours.

There is no central point of failure and no bottleneck: all nodes have the same role.

Every message is sent over its own TCP connection, with Java serialization, and with an artificial delay of
20 to 150 ms per hop. The channels are not FIFO and the protocol does not rely on ordering. The destination address
is always read from the data about the node (it is never fixed). A message of an unknown type, or one that does not
follow the protocol, is discarded.

## 5. Message format

All messages are objects of the class `servant.message.Message` with these fields, in this order:

| Field | Meaning |
|---|---|
| `id` | Unique identifier of the message (identifier of the sender plus a sequence number) |
| `type` | Type of the message (`servant.message.MessageType`) |
| `sender` | Node that created the message (ip, port, team, start moment) |
| `receiver` | Final destination; null means broadcast |
| `lastHop` | Node that forwarded the message last |
| `hops` | Number of hops so far (the message is discarded after 32) |
| `replyTo` | Id of the request this message answers, or null |
| `direct` | True if the message goes straight to the address of the destination, without forwarding |
| `payload` | Content, depends on the type (`servant.message.Payloads`) |

## 6. List of messages

**Joining (direct messages)**

| Type | Direction | Content |
|---|---|---|
| `SAY_HI_TO_BOOTSTRAP` | new node to bootstrap | none |
| `BOOTSTRAP_SAYS_HELLO` | bootstrap to new node | Reply. The node to contact, or null if the system is empty |
| `SAY_HI_TO_SYSTEM_NODE` | new node to system node | none |
| `SYSTEM_NODE_SAYS_WELCOME` | system node to new node | Reply. `Welcome(members, nodes that left, file index, token epoch)` |
| `JOINED_SYSTEM` | new node to bootstrap | none; the bootstrap adds the sender to its list |
| `LEFT_SYSTEM` | node to bootstrap | The node that is no longer in the system |

**Membership (broadcast)**

| Type | Content |
|---|---|
| `NEW_NODE_ARRIVAL` | The new node |
| `NODE_REMOVAL` | `NodeRemoval(node, graceful)`. `graceful` is true for quit and false for a failure. Also sent directly to a node that was removed but still reports in. |

**Failure detection and state reconciliation (direct messages)**

| Type | Content |
|---|---|
| `IS_OK` | Ping. `StateDigest(digest of the member set, digest of the index)` |
| `OK` | Reply to a ping. `StateDigest` |
| `CHECK_SUSPECT` | Request to another node to check a suspicious node. Content: the suspicious node |
| `SUSPECT_RESULT` | Reply. True if the suspicious node did not answer that node either |
| `UPDATE_SYSTEM` | `StateSync(members, nodes that left, index)`. Sent to a neighbour whose digest differs |

**Mutex**

| Type | Content |
|---|---|
| `ASK_FOR_TOKEN` | Broadcast. `TokenRequest(node, sequence number of the request)` |
| `TOKEN` | Forwarded to the node whose turn it is. `Token(LN map, waiting queue, file index, epoch, change counter)` |
| `TOKEN_QUERY` | Broadcast. Content: epoch of the query. The question "who has the token?" |
| `TOKEN_HAVE` | Reply to the coordinator from the node that has the token. Content: epoch of the query |
| `TOKEN_REGENERATED` | Broadcast. Content: the new epoch. Tokens from older epochs are discarded |

**Files**

| Type | Content |
|---|---|
| `ADD_FILE` | `FileData(path, version, content, propagate)`: store one version. With `propagate` set to true the receiver holds the team copy and it is the one that refreshes the global copy |
| `FILE_STORED` | Reply (acknowledgement) to `ADD_FILE` |
| `REQUEST_FILE` | `FileRef(path, version)` |
| `GIVE_FILE` | Reply. `FileData(path, version, content)`; the content is null if the node does not have that version |
| `REPLICATE_FILES` | `FileBundle(path, all versions)`: sent to a node that became responsible for the file through restructuring |
| `UPDATE_INDEX` | Broadcast. `FileMeta(path, latest version, removed, stamp, who works with the file per team, team copies)` |

## 7. Joining

1. New node to bootstrap: `SAY_HI_TO_BOOTSTRAP`.
2. Bootstrap to new node: `BOOTSTRAP_SAYS_HELLO`. If the system is empty the new node is the first one: it creates the token and is done.
3. New node to system node: `SAY_HI_TO_SYSTEM_NODE`.
4. The system node locks the mutex, adds the new node, broadcasts `NEW_NODE_ARRIVAL`, answers `SYSTEM_NODE_SAYS_WELCOME` and unlocks the mutex.
5. New node to bootstrap: `JOINED_SYSTEM`.

If the node from step 3 does not answer, the new node reports that to the bootstrap (`LEFT_SYSTEM`) and starts over.

After every change of membership each node recomputes its neighbours and who is responsible for which file (section 10).

Nodes can join and leave at the same time. The member set is merged as a set (union of the members minus those that
left). Neighbours whose states differ notice it through the digests in the ping messages and exchange their states
(`UPDATE_SYSTEM`).

## 8. Orderly shutdown (quit)

The node locks the mutex, broadcasts `NODE_REMOVAL` with `graceful` set to true, reports `LEFT_SYSTEM` to the
bootstrap, sends its files to the nodes that become responsible for them, hands the token to the next node and stops.

## 9. Failure detection

Every node pings its neighbours (`IS_OK` and `OK`) at a third of the weak limit.

* A neighbour that is silent for longer than the weak limit becomes **suspicious**. It is not removed. Another
  neighbour is asked for confirmation (`CHECK_SUSPECT`). That node pings the suspicious node itself and answers
  `SUSPECT_RESULT`.
* If the suspicion is confirmed and the strong limit expires, the node is removed: `NODE_REMOVAL` with `graceful`
  set to false goes to everyone, `LEFT_SYSTEM` goes to the bootstrap, and restructuring starts.
* If the suspicious node reports in before that, the suspicion is withdrawn.

A node that learns that the system declared it dead shuts down. It can be started again and join as a new node.

## 10. Data placement and fault tolerance

A file is stored on 3 nodes. The hash of the path is taken, then the first node whose address hash is greater or
equal, and the next two by hash (consistent hashing). Files are therefore spread evenly over the whole system, and
three copies survive the simultaneous failure of any two nodes. All versions are kept.

**Restructuring.** When the membership changes, every node computes the old and the new responsible nodes for each
file in its storage. It sends all versions to a node that just became responsible (`REPLICATE_FILES`), and if it is
no longer responsible itself it deletes its copy. After a failure the number of copies returns to 3 this way.

The virtual file system exists as long as at least one node is active.

## 11. Mutex (Suzuki Kasami)

All changes of the file index (add, pull, commit, remove, a node joining, a node quitting) are made under the mutex.

* **Lock.** If the node has the token, it enters. Otherwise it increases its RN and broadcasts `ASK_FOR_TOKEN`. It
  repeats the request every 3 s (with a higher sequence number) until the token arrives, so a lost message does not
  stop the system.
* **Request.** Every node remembers the highest sequence number it has seen (RN). A node that holds the token and
  does not use it sends it to a node whose RN is greater than the LN in the token.
* **Unlock.** LN of this node is set to its RN. All nodes with RN greater than LN are added to the waiting queue of
  the token (starting from the next one in the ring). The token is sent to the first in the queue. If nobody waits,
  the token stays at the node.

The token also carries the authoritative file index, so a node that enters the critical section always sees the
latest state.

**Token recovery.** After a node fails, the coordinator (the first node in the ring) broadcasts `TOKEN_QUERY` with a
new epoch. A node that has the token answers `TOKEN_HAVE`. If nobody answers within 4 s, the coordinator creates a
new token and broadcasts `TOKEN_REGENERATED` with an even higher epoch; a token from an older epoch is discarded
wherever it arrives. A node that has been waiting for the token for more than 15 s starts the same procedure.

## 12. Versioning and the index

Every node has the index (path to latest version, whether the file is removed, team data). A change is made under
the mutex, written into the token and broadcast (`UPDATE_INDEX`). Every entry has a growing stamp; on merge the
higher stamp wins.

| Operation | What happens |
|---|---|
| `add` | Checks that the path does not exist, stores version 0 on the responsible nodes, writes the entry |
| `commit` | Compares the version the user started from with the latest one in the index. If the index is ahead it is a conflict, otherwise the next version is stored |
| `pull` | Reads the content of the requested version from the responsible nodes (if one does not answer, the next is asked) |
| `remove` | Records that the file is removed; every node that stores it deletes it from its storage |

## 13. Team optimisation

The entry of a file records, for every team, which members work with it (they did `add` or `pull`).
When that number exceeds the limit and the team has no copy of its own yet, the node that just fetched the file
creates a team copy in its storage and records that in the index. From then on:

* Members of the team fetch the file from the team copy first. It is also topologically close to them, because the
  team is adjacent in the ring.
* A commit by a member of the team is sent to the node with the team copy (`ADD_FILE` with `propagate` set to
  true), and only that node refreshes the global copy.
* A commit from another team refreshes the global copy and all team copies.

If the node with the team copy leaves the system, the entry is cleaned and the team returns to the global copy.

## 14. Limitations

* Every node knows the whole member set (it talks directly only to a logarithmic number of neighbours).
* The mutex is global: one file operation runs at a time in the whole system.
* A team copy keeps the versions from the moment it was created; older versions are read from the global copy.
* If all three nodes that store a file fail at the same time, the file is lost.
* The work root remembers the version the user started from only while the node is running.
