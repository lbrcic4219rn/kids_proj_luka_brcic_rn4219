package servant.message;

import java.io.Serializable;

public enum MessageType implements Serializable {

    // add_new_node_messages:
    SAY_HI_TO_BOOTSTRAP, BOOTSTRAP_SAYS_HELLO, SAY_HI_TO_SYSTEM_NODE, SYSTEM_NODE_SAYS_WELCOME,

    // broadcast_messages:
    ADD_FILE, DELETE_FILE, NEW_NODE_ARRIVAL, NODE_REMOVAL, UPDATE_SYSTEM, SYSTEM_IS_UPDATED,

    // health_check_messages (ping):
    IS_OK, NOT_OK, OK,

    // mutex_messages (token):
    ASK_FOR_TOKEN, TOKEN,

    // poison_messages:
    POISON,

    // user_requests_messages (pull & push)
    REQUEST_FILE, GIVE_FILE

}
