
package quanta.response;

import quanta.response.base.ResponseBase;

public class FileSearchResponse extends ResponseBase {
	private String searchResultNodeId;

	
	public String getSearchResultNodeId() {
		return this.searchResultNodeId;
	}

	
	public void setSearchResultNodeId(final String searchResultNodeId) {
		this.searchResultNodeId = searchResultNodeId;
	}

	public FileSearchResponse() {
	}
}
