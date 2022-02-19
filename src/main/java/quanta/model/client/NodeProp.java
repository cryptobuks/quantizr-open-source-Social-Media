package quanta.model.client;

import com.fasterxml.jackson.annotation.JsonValue;

public enum NodeProp {
    // Activity Pub Properties
    ACT_PUB_ID("apid", "s"), //
    ACT_PUB_OBJ_TYPE("ap:objType", "s"), //

    ACT_PUB_OBJ_CONTENT("ap:objContent", "s"), //

    // For "AP Note" Objects (per spec) this is the "inReplyTo" property on the node.
    ACT_PUB_OBJ_INREPLYTO("ap:objInReplyTo", "s"), //

    // For "AP Note" Objects (per spec) this is the "url" property on the node.
    ACT_PUB_OBJ_URL("ap:objUrl", "s"), //

    ACT_PUB_OBJ_ATTRIBUTED_TO("ap:objAttributedTo", "s"), //
    ACT_PUB_USER_ICON_URL("ap:userIcon", "s"), //
    ACT_PUB_SHARED_INBOX("ap:sharedInbox", "s"), //
    ACT_PUB_USER_IMAGE_URL("ap:userImage", "s"), //

    // points to URL of actor object (APObj.id prop on actual Actor Objects)
    ACT_PUB_ACTOR_ID("ap:actorId", "s"), //

    // this is the url of the HTML for the user (APObj.url prop on actual Actor Objects)
    ACT_PUB_ACTOR_URL("ap:actorUrl", "s"), //

    ACT_PUB_ACTOR_INBOX("ap:actorInbox", "s"), //
    ACT_PUB_SENSITIVE("ap:nsfw", "s"), //

    // this 's' probably needs to change to JSON or Object ? (todo-1)
    ACT_PUB_TAG("ap:tag", "s"), //

    /*
     * This is the encrypted symetric key to the node data, that was encrypted using the private key of
     * the owner of the node. When nodes are shared to other users the cleartext copy of this key is
     * encrypted with the public key of the user it's being shared to so that user can use their private
     * key to decrypt this key and gain access to the actual data.
     */
    ENC_KEY("sn:encKey", "s"),

    // finding this on a node means it can be safely deleted without affecting any
    // local users
    TEMP("tmp", "s"),

    /*
     * this is the one entered by the admin which DEFINES the feed, and is not to be overwritten ever by
     * the code
     */
    RSS_FEED_SRC("sn:rssFeedSrc", "s"), AUDIO_URL("sn:audioUrl", "s"),

    USER_PREF_PUBLIC_KEY("sn:publicKey", "s"), //
    USER_PREF_EDIT_MODE("sn:editMode", "s"), //
    USER_PREF_SHOW_METADATA("sn:showMetaData", "s"), //
    USER_PREF_NSFW("sn:nsfw", "s"), //
    USER_PREF_SHOW_PARENTS("sn:showParents", "s"), //
    USER_PREF_PASSWORD_RESET_AUTHCODE("sn:pwdResetAuth", "s"), //
    USER_PREF_RSS_HEADINGS_ONLY("sn:rssHeadingsOnly", "s"), //
    USER_PREF_MAIN_PANEL_COLS("sn:mainPanelCols", "s"), //
    SIGNUP_PENDING("sn:signupPending", "s"), //

    EMAIL_CONTENT("sn:content", "s"), //
    EMAIL_RECIP("sn:recip", "s"), //
    EMAIL_SUBJECT("sn:subject", "s"), //

    /*
     * when a node id pointing to some other node, we set it's target id to the node it points to. For
     * now used only for inbox to point to nodes
     */
    TARGET_ID("sn:target_id", "s"), //

    /*
     * WARNING: Only the User Account Root nodes have this property of the actual user name. All other
     * nodes reference their OWNER as an OwnerId that points to these nodes.
     */
    USER("sn:user", "s"), //
    DISPLAY_NAME("sn:displayName", "s"), //
    USER_BIO("sn:userBio", "s"), //
    USER_TAGS("sn:tags", "s"), //
    PWD_HASH("sn:pwdHash", "s"), //

    FILE_SYNC_LINK("fs:link", "s"), //

    // Goes on 'Friend' nodes, and is added automatically by server (not user)
    USER_NODE_ID("sn:userNodeId", "s"), //
    NAME("sn:name", "s"), //

    // FS_FILE("fs:file"), //
    // FS_FOLDER("fs:folder"), //
    // FS_LUCENE("fs:lucene"), //

    // for inlining an image or other attachment as a single resource on a node use this (link)
    IPFS_LINK("ipfs:link", "s"), //

    // To create an "Explorable" (by user) tree of content assign this property to a node, and set the
    // type of the node to "sn:ipfsNode" (IPFS_NODE)
    IPFS_CID("ipfs:cid", "s"), IPNS_CID("ipns:cid", "s"),

    // Represents an IPFS CID that is aht "Source" for the data in such a way that we assume
    // the IPFS content will supercede (and take precedence), so that we can always read from IPFS
    // and make that content be the content in our DB with fear of overwriting anything.
    IPFS_SCID("ipfs:scid", "s"),

    // When a node has this IPFS_REF property it means the IPFS_LINK on the node is completely external
    // to us
    // and might not even be pinned on our gateway. REF means reference (external reference, from some
    // other gateway)
    IPFS_REF("ipfs:ref", "s"), //

    JSON_HASH("ipfs:json", "s"), //
    SAVE_TO_IPFS("sn:saveToIpfs", "s"), //
    IPFS_LINK_NAME("ipfs:linkName", "s"), //

    // This property indicates that it's data is sourced from IPFS files, and can
    // come from another server
    IPFS_SOURCE("ipfs:source", "s"), //

    FS_LINK("fs:link", "s"), //

    /*
     * property used to indicate we should not query the IPFS network again for this conten becasue we
     * have already loaded it
     */
    IPFS_OK("ipfs:ok", "s"), //

    /*
     * mime type expressed as a file extension. Invented so we can set 'txt' v.s. 'md' to turn off
     * metadata rendering
     */
    MIME_EXT("sn:ext", "s"), //

    EMAIL("sn:email", "s"), //
    CODE("sn:code", "s"), //

    BIN_MIME("sn:mimeType", "s"), //
    BIN_FILENAME("sn:fileName", "s"), //
    BIN_SIZE("sn:size", "s"), //

    /*
     * if this is present it indicates we have a "data:" url stored here which means we hava an image
     * (for example) with data encoded inline, and this data url will be stored as text in the
     * 'attachment' of the node
     */
    BIN_DATA_URL("sn:dataUrl", "s"), //

    JSON_FILE_SEARCH_RESULT("sn:json", "s"), //
    NOWRAP("sn:nowrap", "s"), //

    BIN_DATA("sn:jcrData", "s"), //
    BIN("bin", "s"), //

    // Attachments that are not stored locally but by external url use this.
    BIN_URL("sn:extUrl", "s"),

    IMG_WIDTH("sn:imgWidth", "s"), //
    IMG_HEIGHT("sn:imgHeight", "s"), //
    IMG_SIZE("sn:imgSize", "s"), //
    CHILDREN_IMG_SIZES("sn:childrenImgSizes", "s"), //

    // get how many bytes of storage the user currently holds
    BIN_TOTAL("sn:binTot", "s"), //

    // amount of bytes the user is ALLOWED to save.
    BIN_QUOTA("sn:binQuota", "s"), //

    LAST_LOGIN_TIME("sn:lastLogin", "s"), //
    LAST_ACTIVE_TIME("sn:lastActive", "s"), //

    /*
     * NOTE: These two crypto keys are ONLY used for ActivityPub.
     * 
     * For the E2E Encryption capabilities of the platform, the "sn:publicKey" is the key that's used
     * and only the public key exists on the server for that E2E encryption. In other words,
     * "sn:publicKey" is used to encrypt actual data and these two defined here are only used as part of
     * the ActivityPub user authentication process.
     */
    CRYPTO_KEY_PUBLIC("sn:cryptoKeyPublic", "s"), //
    CRYPTO_KEY_PRIVATE("sn:cryptoKeyPrivate", "s"), //

    INLINE_CHILDREN("inlineChildren", "s"), //
    
    PRIORITY("priority", "s"), //
    PRIORITY_FULL("p.priority", "s"), //

    LAYOUT("layout", "s"), //
    ORDER_BY("orderBy", "s"), NO_OPEN_GRAPH("noOpenGraph", "s"), //

    TYPE_LOCK("sn:typLoc", "s"), //
    
    DATE("date", "s"), //
    DATE_FULL("p.date", "s"), //

    DURATION("duration", "s");

    @JsonValue
    private final String value;

    private final String type;

    private NodeProp(String value, String type) {
        this.value = value;
        this.type = type;
    }

    public String toString() {
        return value;
    }

    public String s() {
        return value;
    }

    public String getType() {
        return type;
    }
}
