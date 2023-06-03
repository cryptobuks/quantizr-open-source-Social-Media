
package quanta.request;

import java.util.List;
import quanta.request.base.RequestBase;

public class JoinNodesRequest extends RequestBase {
	private List<String> nodeIds;
	
	public List<String> getNodeIds() {
		return this.nodeIds;
	}
	
	public void setNodeIds(final List<String> nodeIds) {
		this.nodeIds = nodeIds;
	}

	public JoinNodesRequest() {
	}
}
