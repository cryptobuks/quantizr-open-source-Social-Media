
package quanta.request;

import quanta.request.base.RequestBase;

public class FileSearchRequest extends RequestBase {
	private String searchText;
	private boolean reindex;
	/* Node user has selected when running the command */
	private String nodeId;
	
	public String getSearchText() {
		return this.searchText;
	}
	
	public boolean isReindex() {
		return this.reindex;
	}

	public String getNodeId() {
		return this.nodeId;
	}
	
	public void setSearchText(final String searchText) {
		this.searchText = searchText;
	}
	
	public void setReindex(final boolean reindex) {
		this.reindex = reindex;
	}

	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public FileSearchRequest() {
	}
}
