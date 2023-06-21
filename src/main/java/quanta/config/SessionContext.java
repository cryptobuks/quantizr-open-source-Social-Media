package quanta.config;

import java.io.Serializable;
import java.security.PublicKey;
import org.bson.types.ObjectId;
import quanta.model.UserPreferences;
import quanta.model.client.PrincipalName;
import quanta.mongo.MongoUtil;

/**
 * Session object holding state per user session.
 *
 * 1: Need to refactor so that the only session-specific data are things that apply to THIS server
 * node instance and wouldn't be something that would make load balancer nodes have issues.
 *
 * Serializable for saving to Redis
 */
public class SessionContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private String command;
    private String urlIdFailMsg;
    private String userMsg;
    private String displayUserProfileId;
    private String initialNodeId;
    private String loadNostrId;
    private String loadNostrIdRelays;
    private PublicKey pubSigKey = null;

    private boolean live = true;
    private String rootId;
    /*
     * When the user does a "Timeline" search we store the path of the node the timeline was done on so
     * that with a simple substring search, we can detect any time a new node is added that would've
     * appeared in the timeline and then do a server push to browsers of any new nodes, thereby creating
     * a realtime view of the timeline, making it become like a "chat room"
     */
    private String timelinePath;
    private String userName = PrincipalName.ANON.s();
    private ObjectId userNodeId;
    private String timezone;
    private String timeZoneAbbrev;
    private String allowedFeatures = "";
    private long lastLoginTime;
    private long lastActiveTime;
    private UserPreferences userPreferences;
    private String userToken;
    private boolean enableIPSM;
    // this gets set to true, to trigger a refresh when needed again.
    private boolean friendsTagsDirty;
    /*
     * When the user is viewing the Node Feed for a specific node, this will be the path of that root
     * node, and we use this so we can easily do a 'browser push' to any user whenever something new is
     * created under a that feed. todo-2: we could rename this to "chatNodePath", because it's basically
     * the chat node when the user is in a chat room.
     */
    private String watchingPath;

    public SessionContext() {}

    public PublicKey getPubSigKey() {
        return pubSigKey;
    }

    public void setPubSigKey(PublicKey pubSigKey) {
        this.pubSigKey = pubSigKey;
    }

    public boolean isEnableIPSM() {
        return enableIPSM;
    }

    public void setEnableIPSM(boolean enableIPSM) {
        this.enableIPSM = enableIPSM;
    }

    /* Extra layer of security to invalidate this session object */
    public void forceAnonymous() {
        userToken = null;
        userName = PrincipalName.ANON.s();
        rootId = null;
        userNodeId = null;
        timelinePath = null;
        watchingPath = null;
    }

    public String getUserToken() {
        return userToken;
    }

    public void setUserToken(String userToken) {
        this.userToken = userToken;
    }

    public boolean isAdmin() {
        return PrincipalName.ADMIN.s().equalsIgnoreCase(userName);
    }

    public boolean isAnonUser() {
        return PrincipalName.ANON.s().equalsIgnoreCase(userName);
    }

    public boolean isTestAccount() {
        return MongoUtil.isTestAccountName(userName);
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getTimeZoneAbbrev() {
        return timeZoneAbbrev;
    }

    public void setTimeZoneAbbrev(String timeZoneAbbrev) {
        this.timeZoneAbbrev = timeZoneAbbrev;
    }

    public String getRootId() {
        return rootId;
    }

    public void setRootId(String rootId) {
        this.rootId = rootId;
    }

    public UserPreferences getUserPreferences() {
        return userPreferences;
    }

    public void setUserPreferences(UserPreferences userPreferences) {
        this.userPreferences = userPreferences;
    }

    public long getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(long lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public String getTimelinePath() {
        return timelinePath;
    }

    public void setTimelinePath(String timelinePath) {
        this.timelinePath = timelinePath;
    }

    public boolean isLive() {
        return live;
    }

    public void setLive(boolean live) {
        this.live = live;
    }

    public String getWatchingPath() {
        return watchingPath;
    }

    public void setWatchingPath(String watchingPath) {
        this.watchingPath = watchingPath;
    }

    public ObjectId getUserNodeId() {
        return userNodeId;
    }

    public void setUserNodeId(ObjectId userNodeId) {
        this.userNodeId = userNodeId;
    }

    public String getAllowedFeatures() {
        return allowedFeatures;
    }

    public void setAllowedFeatures(String allowedFeatures) {
        this.allowedFeatures = allowedFeatures;
    }

    public boolean allowWeb3() {
        // turning on for everyone for now
        return true;
        // return getAllowedFeatures().contains("web3");
    }

    public boolean isFriendsTagsDirty() {
        return friendsTagsDirty;
    }

    public void setFriendsTagsDirty(boolean friendsTagsDirty) {
        this.friendsTagsDirty = friendsTagsDirty;
    }

    public String getUrlIdFailMsg() {
        return urlIdFailMsg;
    }

    public void setUrlIdFailMsg(String urlIdFailMsg) {
        this.urlIdFailMsg = urlIdFailMsg;
    }

    public String getUserMsg() {
        return userMsg;
    }

    public void setUserMsg(String userMsg) {
        this.userMsg = userMsg;
    }

    public String getDisplayUserProfileId() {
        return displayUserProfileId;
    }

    public void setDisplayUserProfileId(String displayUserProfileId) {
        this.displayUserProfileId = displayUserProfileId;
    }

    public String getInitialNodeId() {
        return initialNodeId;
    }

    public void setInitialNodeId(String initialNodeId) {
        this.initialNodeId = initialNodeId;
    }

    public String getLoadNostrId() {
        return loadNostrId;
    }

    public void setLoadNostrId(String loadNostrId) {
        this.loadNostrId = loadNostrId;
    }

    public String getLoadNostrIdRelays() {
        return loadNostrIdRelays;
    }

    public void setLoadNostrIdRelays(String loadNostrIdRelays) {
        this.loadNostrIdRelays = loadNostrIdRelays;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
