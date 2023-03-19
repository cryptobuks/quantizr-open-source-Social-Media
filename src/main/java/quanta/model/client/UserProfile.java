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

	public String getUserName() {
		return userName;
	}

	public String getUserBio() {
		return userBio;
	}

	public void setUserBio(String userBio) {
		this.userBio = userBio;
	}

	public String getUserTags() {
		return userTags;
	}

	public void setUserTags(String userTags) {
		this.userTags = userTags;
	}

	public String getBlockedWords() {
		return blockedWords;
	}

	public void setBlockedWords(String blockedWords) {
		this.blockedWords = blockedWords;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getAvatarVer() {
		return avatarVer;
	}

	public void setAvatarVer(String avatarVer) {
		this.avatarVer = avatarVer;
	}

	public String getUserNodeId() {
		return this.userNodeId;
	}

	public void setUserNodeId(String userNodeId) {
		this.userNodeId = userNodeId;
	}

	public String getHeaderImageVer() {
		return headerImageVer;
	}

	public void setHeaderImageVer(String headerImageVer) {
		this.headerImageVer = headerImageVer;
	}

	public String getApIconUrl() {
		return apIconUrl;
	}

	public void setApIconUrl(String apIconUrl) {
		this.apIconUrl = apIconUrl;
	}

	public String getApImageUrl() {
		return apImageUrl;
	}

	public void setApImageUrl(String apImageUrl) {
		this.apImageUrl = apImageUrl;
	}

	public String getActorUrl() {
		return actorUrl;
	}

	public void setActorUrl(String actorUrl) {
		this.actorUrl = actorUrl;
	}

	public String getActorId() {
		return actorId;
	}

	public void setActorId(String actorId) {
		this.actorId = actorId;
	}

	public String getHomeNodeId() {
		return homeNodeId;
	}

	public void setHomeNodeId(String homeNodeId) {
		this.homeNodeId = homeNodeId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public int getFollowerCount() {
		return followerCount;
	}

	public void setFollowerCount(int followerCount) {
		this.followerCount = followerCount;
	}

	public int getFollowingCount() {
		return followingCount;
	}

	public void setFollowingCount(int followingCount) {
		this.followingCount = followingCount;
	}

	public boolean isFollowing() {
		return following;
	}

	public void setFollowing(boolean following) {
		this.following = following;
	}

	public boolean isBlocked() {
		return blocked;
	}

	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
	}

	public String getDidIPNS() {
		return didIPNS;
	}

	public void setDidIPNS(String didIPNS) {
		this.didIPNS = didIPNS;
	}

	public boolean isMfsEnable() {
		return mfsEnable;
	}

	public void setMfsEnable(boolean mfsEnable) {
		this.mfsEnable = mfsEnable;
	}
}
