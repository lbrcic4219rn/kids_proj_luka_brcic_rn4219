# SillyGit

A distributed version control system for plain text files, written in Java for the Concurrent and Distributed
Systems course (KiDS) at the School of Computing in Belgrade. Nodes form a ring, keep every file on three machines,
detect failed nodes and recover from them, and coordinate through a Suzuki Kasami token mutex.

The assignment text is in `KiDS Projekat.pdf` (Serbian). The full protocol description is in
[DOCUMENTATION.md](DOCUMENTATION.md).

## Features

* **Versioning.** `add`, `pull name [version]`, `commit`, `remove`, for single files and whole directories. Every version is kept.
* **Conflict resolution.** When two nodes change the same file, the second one chooses between `view`, `push` and `pull`.
* **No central node.** A bootstrap server only introduces a new node to the system. Every node has a logarithmic
  number of neighbours and messages reach any node in a logarithmic number of hops.
* **Fault tolerance.** Nodes ping their neighbours. A silent node is first marked suspicious, a second node confirms
  it, and after the strong limit it is removed. Every file lives on three nodes, so the data survives two nodes
  failing at once, and the copies are restored afterwards.
* **Distributed mutex.** Suzuki Kasami with a token. If the node holding the token dies, a new token is generated.
* **Team optimisation.** When enough members of one team work with a file, a copy is created inside the team and
  the team uses that copy first.

## Requirements

* JDK 17 or newer
* Nothing else: the Gradle wrapper is included

## Build

```bash
./gradlew build
```

This compiles the project and runs the unit tests.

## Run

Start the bootstrap server in one terminal:

```bash
java -cp build/classes/java/main app.BootstrapServer
```

Start each node in its own terminal, with its own configuration file:

```bash
java -cp build/classes/java/main app.ServantMain config/servent0.properties
```

Six configurations are provided, `config/servent0.properties` to `config/servent5.properties`. The first three
nodes are in team `a`, the other three in team `b`.

The same can be done through Gradle with `./gradlew runBootstrap` and
`./gradlew run --args="config/servent0.properties"`.

The bootstrap server listens on port 1000 by default. On systems that do not allow that port for ordinary users,
pass another one (`app.BootstrapServer 2000`) and change `bootstrap_port` in the configuration files.

## Using a node

Each node has a work root (for node 0 that is `workplace/servent0/work`). Put files there, then type commands in
the terminal of the node.

| Command | Effect |
|---|---|
| `add name` | Adds a file or a directory from the work root to the system as version 0 |
| `pull name [version]` | Fetches a file or a directory into the work root; the latest version unless one is given |
| `commit name` | Stores the modified file as a new version |
| `remove name` | Removes a file or a directory from the system |
| `info` | Shows the members, the neighbours, the token, the file index, the storage and the work root |
| `quit` | Leaves the system in an orderly way |

A short session with two nodes:

```text
node 0>  add notes.txt            notes.txt becomes version 0
node 1>  pull notes.txt           node 1 gets version 0
node 1>  (edit the file)
node 1>  commit notes.txt         notes.txt becomes version 1
node 0>  (edit the file)
node 0>  commit notes.txt         conflict: node 0 started from version 0
node 0>  view                     saves the system version as notes.txt.conflict
node 0>  push                     the local file becomes version 2
```

The `sample-files` directory contains a few text files to try this with.

To simulate a failure, kill the process of a node. The other nodes report it as suspicious after the weak limit
and remove it after the strong limit.

## Configuration

| Attribute | Meaning |
|---|---|
| `work_root` | Directory with the files the user works on |
| `storage` | Directory where the node keeps the files it is responsible for |
| `ip`, `port` | Address the node listens on |
| `bootstrap_ip`, `bootstrap_port` | Address of the bootstrap server |
| `weak_limit` | Time in ms without an answer after which a node is suspicious |
| `strong_limit` | Time in ms without an answer after which a confirmed suspicious node is removed |
| `team` | Team of the node, lowercase letters |
| `limit` | Number of team members working with a file above which a team copy is created |

## Project layout

| Path | Contents |
|---|---|
| `src/main/java/app` | Startup, configuration, membership, ring and routing |
| `src/main/java/servant` | Listener, message format, sending and forwarding |
| `src/main/java/mutex` | Suzuki Kasami mutex and the token |
| `src/main/java/failure` | Failure detector |
| `src/main/java/file` | File index, storage, work root and the file operations |
| `src/main/java/cli` | Command parser and the commands |
| `src/test/java` | Unit tests for the ring, routing, file placement and the index |
| `config` | Node configurations for a local run |
| `sample-files` | Text files for trying the commands |

## Limitations

* Every node knows the whole member list, although it talks directly only to its neighbours.
* The mutex is global, so one file operation runs at a time in the whole system.
* A node that the system wrongly declared dead shuts down and has to be started again.
* A file is lost if all three nodes that store it fail at the same time.

More detail on all of this is in [DOCUMENTATION.md](DOCUMENTATION.md).
