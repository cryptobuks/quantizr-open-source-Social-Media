
package quanta.request;

import quanta.request.base.RequestBase;

public class RemovePrivilegeRequest extends RequestBase {
	private String nodeId;
	private String principalNodeId;
	/* for now only 'public' is the only option we support */
	private String privilege;

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public String getPrincipalNodeId() {
		return this.principalNodeId;
	}

	
	public String getPrivilege() {
		return this.privilege;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setPrincipalNodeId(final String principalNodeId) {
		this.principalNodeId = principalNodeId;
	}

	
	public void setPrivilege(final String privilege) {
		this.privilege = privilege;
	}

	public RemovePrivilegeRequest() {
	}
}
