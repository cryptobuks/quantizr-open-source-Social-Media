package quanta.model.client;

import com.fasterxml.jackson.annotation.JsonValue;

public enum NodeType {

    // INTERNAL types
    ACCOUNT("sn:account"),//
    REPO_ROOT("sn:repoRoot"), //
    INBOX("sn:inbox"), //
    INBOX_ENTRY("sn:inboxEntry"), //
    ROOM("sn:room"), //
    NOTES("sn:notes"), //
    BOOKMARK("sn:bookmark"), //
    BOOKMARK_LIST("sn:bookmarkList"), //
    EXPORTS("sn:exports"), //
    CALCULATOR("sn:calculator"),//
    CALENDAR("sn:calendar"),//
    COMMENT("sn:comment"), //
    
    RSS_FEED("sn:rssfeed"), //
    RSS_FEEDS("sn:rssfeeds"), //
    FRIEND_LIST("sn:friendList"), //
    BLOCKED_USERS("sn:blockedUsers"), //
    FRIEND("sn:friend"), //
    POSTS("sn:posts"), //
    ACT_PUB_POSTS("ap:posts"), //

    NONE("u"), //same as 'markdown' really (untyped, default to markdown)
    NOSTR_ENC_DM("sn:ned"), // 
    PLAIN_TEXT("sn:txt"), //
    FS_FILE("fs:file"), //
    FS_FOLDER("fs:folder"), //
    FS_LUCENE("fs:lucene"), //
    IPFS_NODE("sn:ipfsNode");

    @JsonValue
    private final String value;

    private NodeType(String value) {
        this.value = value;
    }

    public String toString() {
        return value;
    }

    public String s() {
        return value;
    }
}