
package quanta.request;

import quanta.request.base.RequestBase;

public class InitNodeEditRequest extends RequestBase {
	private String nodeId;
	// if true, this indicates that the 'nodeId' is the ID of a User's Node, and the caller
	// is wanting to start editing his "Friend Node" representing him following said user.
	private Boolean editMyFriendNode;

	public String getNodeId() {
		return this.nodeId;
	}
	
	public Boolean getEditMyFriendNode() {
		return this.editMyFriendNode;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setEditMyFriendNode(final Boolean editMyFriendNode) {
		this.editMyFriendNode = editMyFriendNode;
	}

	public InitNodeEditRequest() {
	}
}
