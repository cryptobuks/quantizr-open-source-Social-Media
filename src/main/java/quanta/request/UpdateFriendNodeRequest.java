
package quanta.request;

import quanta.request.base.RequestBase;

public class UpdateFriendNodeRequest extends RequestBase {
	private String nodeId;
	private String tags;

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public String getTags() {
		return this.tags;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setTags(final String tags) {
		this.tags = tags;
	}

	public UpdateFriendNodeRequest() {
	}
}
