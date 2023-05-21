package quanta.model.client;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Constant {
    NETWORK_NOSTR("nostr"),
    NETWORK_ACTPUB("ap"),
    SEARCH_TYPE_USER_LOCAL("userLocal"), 
    SEARCH_TYPE_USER_ALL("userAll"), 
    SEARCH_TYPE_USER_FOREIGN("userForeign"), 
    SEARCH_TYPE_USER_NOSTR("userNostr"),

    ENC_TAG("<[ENC]>"),

    FEED_NEW("myNewMessages"),
    FEED_PUB("publicFediverse"),
    FEED_TOFROMME("toFromMe"),
    FEED_TOME("toMe"),
    FEED_MY_MENTIONS("myMentions"),
    FEED_FROMMETOUSER("fromMeToUser"),
    FEED_FROMME("fromMe"),
    FEED_FROMFRIENDS("fromFriends"),
    FEED_LOCAL("local"),  
    FEED_NODEFEED("nodeFeed"),
    
    ATTACHMENT_PRIMARY("p"),
    ATTACHMENT_HEADER("h");

    @JsonValue
    private final String value;

    private Constant(String value) {
        this.value = value;
    }

    public String toString() {
        return value;
    }

    public String s() {
        return value;
    }
}