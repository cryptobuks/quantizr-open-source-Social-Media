
package quanta.request;

import quanta.request.base.RequestBase;

public class RenderCalendarRequest extends RequestBase {
	/* can be node id or path. server interprets correctly no matter which */
	private String nodeId;

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	public RenderCalendarRequest() {
	}
}
