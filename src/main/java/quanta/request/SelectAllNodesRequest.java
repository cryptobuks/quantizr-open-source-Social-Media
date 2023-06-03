
package quanta.request;

import quanta.request.base.RequestBase;

public class SelectAllNodesRequest extends RequestBase {
	private String parentNodeId;

	public String getParentNodeId() {
		return this.parentNodeId;
	}
	
	public void setParentNodeId(final String parentNodeId) {
		this.parentNodeId = parentNodeId;
	}

	public SelectAllNodesRequest() {
	}
}
