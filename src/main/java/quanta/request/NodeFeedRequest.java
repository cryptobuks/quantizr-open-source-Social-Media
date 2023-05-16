package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
}
