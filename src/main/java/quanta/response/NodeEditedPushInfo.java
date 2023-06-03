
package quanta.response;

import quanta.model.NodeInfo;

public class NodeEditedPushInfo extends ServerPushInfo {
	private NodeInfo nodeInfo;

	public NodeEditedPushInfo(NodeInfo nodeInfo) {
		super("nodeEdited");
		this.nodeInfo = nodeInfo;
	}
	
	public NodeInfo getNodeInfo() {
		return this.nodeInfo;
	}
	
	public void setNodeInfo(final NodeInfo nodeInfo) {
		this.nodeInfo = nodeInfo;
	}

	public NodeEditedPushInfo() {
	}
}
