
package quanta.response;

import java.util.List;
import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;

public class NodeSearchResponse extends ResponseBase {
	private List<NodeInfo> searchResults;

	
	public List<NodeInfo> getSearchResults() {
		return this.searchResults;
	}

	
	public void setSearchResults(final List<NodeInfo> searchResults) {
		this.searchResults = searchResults;
	}
	
	public NodeSearchResponse() {
	}
}
