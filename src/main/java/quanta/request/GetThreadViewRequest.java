
package quanta.request;

import quanta.request.base.RequestBase;

public class GetThreadViewRequest extends RequestBase {
	private String nodeId;
	private boolean loadOthers;

	public String getNodeId() {
		return this.nodeId;
	}
	
	public boolean isLoadOthers() {
		return this.loadOthers;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setLoadOthers(final boolean loadOthers) {
		this.loadOthers = loadOthers;
	}
	
	public GetThreadViewRequest() {
	}
}
