
package quanta.response;

import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;

public class InsertBookResponse extends ResponseBase {
	private NodeInfo newNode;

	public NodeInfo getNewNode() {
		return this.newNode;
	}
	
	public void setNewNode(final NodeInfo newNode) {
		this.newNode = newNode;
	}

	public InsertBookResponse() {
	}
}
