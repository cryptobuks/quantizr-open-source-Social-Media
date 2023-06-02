
package quanta.request;

import quanta.request.base.RequestBase;

public class TransferNodeRequest extends RequestBase {
	private boolean recursive;
	private String nodeId;
	private String fromUser;
	private String toUser;
	// transfer, accept, reject
	private String operation;

	
	public boolean isRecursive() {
		return this.recursive;
	}

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public String getFromUser() {
		return this.fromUser;
	}

	
	public String getToUser() {
		return this.toUser;
	}

	
	public String getOperation() {
		return this.operation;
	}

	
	public void setRecursive(final boolean recursive) {
		this.recursive = recursive;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setFromUser(final String fromUser) {
		this.fromUser = fromUser;
	}

	
	public void setToUser(final String toUser) {
		this.toUser = toUser;
	}

	
	public void setOperation(final String operation) {
		this.operation = operation;
	}

	public TransferNodeRequest() {
	}
}
