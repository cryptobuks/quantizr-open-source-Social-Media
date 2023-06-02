
package quanta.response;

import java.util.List;
import quanta.model.AccessControlInfo;
import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;

public class SaveNodeResponse extends ResponseBase {
	private NodeInfo node;
	/*
	 * In cases where the updated node is adding encryption we need to send back all the principalIds
	 * (userNodeIds actually) so the client can generate keys for all of them to send back up to allow
	 * access by these shared users. Unless the node is being encrypted these aclEntries will be null
	 */
	private List<AccessControlInfo> aclEntries;

	
	public NodeInfo getNode() {
		return this.node;
	}

	
	public List<AccessControlInfo> getAclEntries() {
		return this.aclEntries;
	}

	
	public void setNode(final NodeInfo node) {
		this.node = node;
	}

	
	public void setAclEntries(final List<AccessControlInfo> aclEntries) {
		this.aclEntries = aclEntries;
	}

	public SaveNodeResponse() {
	}
}
