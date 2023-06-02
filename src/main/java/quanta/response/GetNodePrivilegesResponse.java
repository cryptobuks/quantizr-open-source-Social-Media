
package quanta.response;

import java.util.List;
import quanta.model.AccessControlInfo;
import quanta.response.base.ResponseBase;

public class GetNodePrivilegesResponse extends ResponseBase {
	private List<AccessControlInfo> aclEntries;

	
	public List<AccessControlInfo> getAclEntries() {
		return this.aclEntries;
	}

	
	public void setAclEntries(final List<AccessControlInfo> aclEntries) {
		this.aclEntries = aclEntries;
	}

	public GetNodePrivilegesResponse() {
	}
}
