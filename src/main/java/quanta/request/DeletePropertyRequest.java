
package quanta.request;

import java.util.List;
import quanta.request.base.RequestBase;

public class DeletePropertyRequest extends RequestBase {
	private String nodeId;
	private List<String> propNames;
	
	public String getNodeId() {
		return this.nodeId;
	}
	
	public List<String> getPropNames() {
		return this.propNames;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setPropNames(final List<String> propNames) {
		this.propNames = propNames;
	}

	public DeletePropertyRequest() {
	}
}
