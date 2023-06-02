
package quanta.request;

import quanta.request.base.RequestBase;

public class SetNodePositionRequest extends RequestBase {
	// node to be moved (id or path)
	private String nodeId;
	// targetName can be: up, down, top, bottom
	private String targetName;

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public String getTargetName() {
		return this.targetName;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setTargetName(final String targetName) {
		this.targetName = targetName;
	}

	public SetNodePositionRequest() {
	}
}
