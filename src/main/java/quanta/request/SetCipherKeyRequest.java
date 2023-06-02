
package quanta.request;

import quanta.request.base.RequestBase;

public class SetCipherKeyRequest extends RequestBase {
	private String nodeId;
	private String principalNodeId;
	private String cipherKey;

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public String getPrincipalNodeId() {
		return this.principalNodeId;
	}

	
	public String getCipherKey() {
		return this.cipherKey;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setPrincipalNodeId(final String principalNodeId) {
		this.principalNodeId = principalNodeId;
	}

	
	public void setCipherKey(final String cipherKey) {
		this.cipherKey = cipherKey;
	}

	public SetCipherKeyRequest() {
	}
}
