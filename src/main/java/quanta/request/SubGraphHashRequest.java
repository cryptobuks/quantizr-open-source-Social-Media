
package quanta.request;

import quanta.request.base.RequestBase;

public class SubGraphHashRequest extends RequestBase {
	private boolean recursive;
	private String nodeId;
	
	public boolean isRecursive() {
		return this.recursive;
	}
	
	public String getNodeId() {
		return this.nodeId;
	}
	
	public void setRecursive(final boolean recursive) {
		this.recursive = recursive;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	public SubGraphHashRequest() {
	}
}
