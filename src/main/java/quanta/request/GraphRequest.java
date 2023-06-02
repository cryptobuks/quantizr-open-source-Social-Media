
package quanta.request;

import quanta.request.base.RequestBase;

public class GraphRequest extends RequestBase {
	/* can be node id or path. server interprets correctly no matter which */
	private String nodeId;
	// optional, to perform search to build a graphical result of that.
	private String searchText;

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public String getSearchText() {
		return this.searchText;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setSearchText(final String searchText) {
		this.searchText = searchText;
	}

	public GraphRequest() {
	}
}
