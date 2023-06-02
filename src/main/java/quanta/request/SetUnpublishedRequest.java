
package quanta.request;

import quanta.request.base.RequestBase;

public class SetUnpublishedRequest extends RequestBase {
	private String nodeId;
	private boolean unpublished;

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public boolean isUnpublished() {
		return this.unpublished;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setUnpublished(final boolean unpublished) {
		this.unpublished = unpublished;
	}

	public SetUnpublishedRequest() {
	}
}
