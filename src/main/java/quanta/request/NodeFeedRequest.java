
package quanta.request;

import quanta.request.base.RequestBase;

public class NodeFeedRequest extends RequestBase {
	// zero offset page of results (page=0 is first page)
	private Integer page;
	/* Note one of the other of these should be non-null, but not both */
	private String nodeId;
	private String toUser;
	private Boolean toMe;
	private Boolean myMentions;
	private Boolean fromMe;
	private Boolean fromFriends;
	private Boolean toPublic;
	private Boolean localOnly;
	private Boolean nsfw;
	private String searchText;
	// users can add hashtags to each Friend Node, and those are passed in to filter to show
	// only friends tagged with this tag
	private String friendsTagSearch;
	private Boolean loadFriendsTags;
	private boolean applyAdminBlocks;
	// textual representation of what kind of request is being done.
	private String name;
	private String protocol; // See: Constant.NETWORK_*

	public Integer getPage() {
		return this.page;
	}
	
	public String getNodeId() {
		return this.nodeId;
	}
	
	public String getToUser() {
		return this.toUser;
	}
	
	public Boolean getToMe() {
		return this.toMe;
	}
	
	public Boolean getMyMentions() {
		return this.myMentions;
	}
	
	public Boolean getFromMe() {
		return this.fromMe;
	}
	
	public Boolean getFromFriends() {
		return this.fromFriends;
	}
	
	public Boolean getToPublic() {
		return this.toPublic;
	}
	
	public Boolean getLocalOnly() {
		return this.localOnly;
	}
	
	public Boolean getNsfw() {
		return this.nsfw;
	}
	
	public String getSearchText() {
		return this.searchText;
	}
	
	public String getFriendsTagSearch() {
		return this.friendsTagSearch;
	}
	
	public Boolean getLoadFriendsTags() {
		return this.loadFriendsTags;
	}
	
	public boolean isApplyAdminBlocks() {
		return this.applyAdminBlocks;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getProtocol() {
		return this.protocol;
	}
	
	public void setPage(final Integer page) {
		this.page = page;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setToUser(final String toUser) {
		this.toUser = toUser;
	}
	
	public void setToMe(final Boolean toMe) {
		this.toMe = toMe;
	}
	
	public void setMyMentions(final Boolean myMentions) {
		this.myMentions = myMentions;
	}
	
	public void setFromMe(final Boolean fromMe) {
		this.fromMe = fromMe;
	}
	
	public void setFromFriends(final Boolean fromFriends) {
		this.fromFriends = fromFriends;
	}
	
	public void setToPublic(final Boolean toPublic) {
		this.toPublic = toPublic;
	}
	
	public void setLocalOnly(final Boolean localOnly) {
		this.localOnly = localOnly;
	}
	
	public void setNsfw(final Boolean nsfw) {
		this.nsfw = nsfw;
	}
	
	public void setSearchText(final String searchText) {
		this.searchText = searchText;
	}
	
	public void setFriendsTagSearch(final String friendsTagSearch) {
		this.friendsTagSearch = friendsTagSearch;
	}
	
	public void setLoadFriendsTags(final Boolean loadFriendsTags) {
		this.loadFriendsTags = loadFriendsTags;
	}
	
	public void setApplyAdminBlocks(final boolean applyAdminBlocks) {
		this.applyAdminBlocks = applyAdminBlocks;
	}
	
	public void setName(final String name) {
		this.name = name;
	}
	
	public void setProtocol(final String protocol) {
		this.protocol = protocol;
	}
	
	public NodeFeedRequest() {
	}
}
