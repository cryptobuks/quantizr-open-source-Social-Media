
package quanta.response;

import java.util.List;
import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;

public class NodeFeedResponse extends ResponseBase {
	private Boolean endReached;
	/* orderablility of children not set in these objects, all will be false */
	private List<NodeInfo> searchResults;
	private List<String> friendHashTags;

	
	public Boolean getEndReached() {
		return this.endReached;
	}

	
	public List<NodeInfo> getSearchResults() {
		return this.searchResults;
	}

	
	public List<String> getFriendHashTags() {
		return this.friendHashTags;
	}

	
	public void setEndReached(final Boolean endReached) {
		this.endReached = endReached;
	}

	
	public void setSearchResults(final List<NodeInfo> searchResults) {
		this.searchResults = searchResults;
	}

	
	public void setFriendHashTags(final List<String> friendHashTags) {
		this.friendHashTags = friendHashTags;
	}

	public NodeFeedResponse() {
	}
}
