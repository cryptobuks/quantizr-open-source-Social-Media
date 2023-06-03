
package quanta.request;

import quanta.request.base.RequestBase;

public class BrowseFolderRequest extends RequestBase {
	private String nodeId;

	public String getNodeId() {
		return this.nodeId;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	public BrowseFolderRequest() {
	}
}
