
package quanta.response;

import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;

public class InitNodeEditResponse extends ResponseBase {
	private NodeInfo nodeInfo;

	
	public NodeInfo getNodeInfo() {
		return this.nodeInfo;
	}

	
	public void setNodeInfo(final NodeInfo nodeInfo) {
		this.nodeInfo = nodeInfo;
	}

	public InitNodeEditResponse() {
	}
}
