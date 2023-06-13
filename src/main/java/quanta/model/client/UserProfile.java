package quanta.model.client;

public class UserProfile {

    private String displayName;
    private String userName;
    // if a node exists named '[userName]:home', then the id of that node is stored here.
    private String homeNodeId;
    private String didIPNS;
    private boolean mfsEnable;
    private String userBio;
    private String userTags;
    private String blockedWords;
    private String recentTypes;
    // version (which is now just the GRID ID) needed to retrieve profile image (account node binary
    // attachment)
    // Moving out of here into getUserProfile
    private String avatarVer;
    private String headerImageVer;
    private String userNodeId;
    /* for foreign users this will point to their user avatar image */
    private String apIconUrl;
    /* for foreign users this will point to their user image (i.e. header image) */
    private String apImageUrl;
    /* for foreign users this will be their actor url */
    private String actorUrl;
    private String actorId;
    private int followerCount;
    private int followingCount;
    /*
     * Indicators to the person querying this info about whether they follow or blocked this user
     */
    private boolean following;
    private boolean blocked;
    private String relays;
    private String nostrNpub;
    private Long nostrTimestamp;

    public String getDisplayName() {
        return this.displayName;
    }

    public String getUserName() {
        return this.userName;
    }

    public String getHomeNodeId() {
        return this.homeNodeId;
    }

    public String getDidIPNS() {
        return this.didIPNS;
    }

    public boolean isMfsEnable() {
        return this.mfsEnable;
    }

    public String getUserBio() {
        return this.userBio;
    }

    public String getUserTags() {
        return this.userTags;
    }

    public String getBlockedWords() {
        return this.blockedWords;
    }

    public String getRecentTypes() {
        return this.recentTypes;
    }

    public String getAvatarVer() {
        return this.avatarVer;
    }

    public String getHeaderImageVer() {
        return this.headerImageVer;
    }

    public String getUserNodeId() {
        return this.userNodeId;
    }

    public String getApIconUrl() {
        return this.apIconUrl;
    }

    public String getApImageUrl() {
        return this.apImageUrl;
    }

    public String getActorUrl() {
        return this.actorUrl;
    }

    public String getActorId() {
        return this.actorId;
    }

    public int getFollowerCount() {
        return this.followerCount;
    }

    public int getFollowingCount() {
        return this.followingCount;
    }

    public boolean isFollowing() {
        return this.following;
    }

    public boolean isBlocked() {
        return this.blocked;
    }

    public String getRelays() {
        return this.relays;
    }

    public String getNostrNpub() {
        return this.nostrNpub;
    }

    public Long getNostrTimestamp() {
        return this.nostrTimestamp;
    }

    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }

    public void setUserName(final String userName) {
        this.userName = userName;
    }

    public void setHomeNodeId(final String homeNodeId) {
        this.homeNodeId = homeNodeId;
    }

    public void setDidIPNS(final String didIPNS) {
        this.didIPNS = didIPNS;
    }

    public void setMfsEnable(final boolean mfsEnable) {
        this.mfsEnable = mfsEnable;
    }

    public void setUserBio(final String userBio) {
        this.userBio = userBio;
    }

    public void setUserTags(final String userTags) {
        this.userTags = userTags;
    }

    public void setBlockedWords(final String blockedWords) {
        this.blockedWords = blockedWords;
    }

    public void setRecentTypes(final String recentTypes) {
        this.recentTypes = recentTypes;
    }

    public void setAvatarVer(final String avatarVer) {
        this.avatarVer = avatarVer;
    }

    public void setHeaderImageVer(final String headerImageVer) {
        this.headerImageVer = headerImageVer;
    }

    public void setUserNodeId(final String userNodeId) {
        this.userNodeId = userNodeId;
    }

    public void setApIconUrl(final String apIconUrl) {
        this.apIconUrl = apIconUrl;
    }

    public void setApImageUrl(final String apImageUrl) {
        this.apImageUrl = apImageUrl;
    }

    public void setActorUrl(final String actorUrl) {
        this.actorUrl = actorUrl;
    }

    public void setActorId(final String actorId) {
        this.actorId = actorId;
    }

    public void setFollowerCount(final int followerCount) {
        this.followerCount = followerCount;
    }

    public void setFollowingCount(final int followingCount) {
        this.followingCount = followingCount;
    }

    public void setFollowing(final boolean following) {
        this.following = following;
    }

    public void setBlocked(final boolean blocked) {
        this.blocked = blocked;
    }

    public void setRelays(final String relays) {
        this.relays = relays;
    }

    public void setNostrNpub(final String nostrNpub) {
        this.nostrNpub = nostrNpub;
    }

    public void setNostrTimestamp(final Long nostrTimestamp) {
        this.nostrTimestamp = nostrTimestamp;
    }

    public UserProfile() {}
}
