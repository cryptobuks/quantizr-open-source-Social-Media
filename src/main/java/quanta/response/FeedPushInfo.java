
package quanta.response;

import quanta.model.NodeInfo;

public class FeedPushInfo extends ServerPushInfo {
	private NodeInfo nodeInfo;

	public FeedPushInfo(NodeInfo nodeInfo) {
		super("feedPush");
		this.nodeInfo = nodeInfo;
	}

	
	public NodeInfo getNodeInfo() {
		return this.nodeInfo;
	}

	
	public void setNodeInfo(final NodeInfo nodeInfo) {
		this.nodeInfo = nodeInfo;
	}

	public FeedPushInfo() {
	}
}
