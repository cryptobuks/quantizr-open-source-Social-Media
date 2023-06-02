
package quanta.response;

import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;

public class CreateSubNodeResponse extends ResponseBase {
	private NodeInfo newNode;
	/*
	 * This is set to true in the response, when the parent node is encrypted and so we default the new
	 * child to be encrypted also.
	 * 
	 * Mainly used in a 'reply' to an encrypted node.
	 */
	private boolean encrypt;

	
	public NodeInfo getNewNode() {
		return this.newNode;
	}

	
	public boolean isEncrypt() {
		return this.encrypt;
	}

	
	public void setNewNode(final NodeInfo newNode) {
		this.newNode = newNode;
	}

	
	public void setEncrypt(final boolean encrypt) {
		this.encrypt = encrypt;
	}

	public CreateSubNodeResponse() {
	}
}
