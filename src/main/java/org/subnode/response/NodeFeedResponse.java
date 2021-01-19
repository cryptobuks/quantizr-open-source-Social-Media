package org.subnode.response;

import java.util.List;

import org.subnode.model.NodeInfo;
import org.subnode.response.base.ResponseBase;

public class NodeFeedResponse extends ResponseBase {

	private Boolean endReached;

	/* orderablility of children not set in these objects, all will be false */
	private List<NodeInfo> searchResults;

	public List<NodeInfo> getSearchResults() {
		return searchResults;
	}

	public void setSearchResults(List<NodeInfo> searchResults) {
		this.searchResults = searchResults;
	}

	public Boolean getEndReached() {
		return endReached;
	}

	public void setEndReached(Boolean endReached) {
		this.endReached = endReached;
	}
}
