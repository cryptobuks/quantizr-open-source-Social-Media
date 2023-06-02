
package quanta.response;

public class FriendInfo {
    private String displayName;
    private String userName;
    private String relays; // note: Relays is not STORED in the actual friend node but only on the account node.
    private String avatarVer;
    private String userNodeId;
    private String friendNodeId;
    private String foreignAvatarUrl;
    private String tags;
    // indicates this user liked some node, and is dependent upon use case where this FriendInfo is
    // being used
    private Boolean liked;

    
    public String getDisplayName() {
        return this.displayName;
    }

    
    public String getUserName() {
        return this.userName;
    }

    
    public String getRelays() {
        return this.relays;
    }

    
    public String getAvatarVer() {
        return this.avatarVer;
    }

    
    public String getUserNodeId() {
        return this.userNodeId;
    }

    
    public String getFriendNodeId() {
        return this.friendNodeId;
    }

    
    public String getForeignAvatarUrl() {
        return this.foreignAvatarUrl;
    }

    
    public String getTags() {
        return this.tags;
    }

    
    public Boolean getLiked() {
        return this.liked;
    }

    
    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }

    
    public void setUserName(final String userName) {
        this.userName = userName;
    }

    
    public void setRelays(final String relays) {
        this.relays = relays;
    }

    
    public void setAvatarVer(final String avatarVer) {
        this.avatarVer = avatarVer;
    }

    
    public void setUserNodeId(final String userNodeId) {
        this.userNodeId = userNodeId;
    }

    
    public void setFriendNodeId(final String friendNodeId) {
        this.friendNodeId = friendNodeId;
    }

    
    public void setForeignAvatarUrl(final String foreignAvatarUrl) {
        this.foreignAvatarUrl = foreignAvatarUrl;
    }

    
    public void setTags(final String tags) {
        this.tags = tags;
    }

    
    public void setLiked(final Boolean liked) {
        this.liked = liked;
    }

    public FriendInfo() {
    }
}
