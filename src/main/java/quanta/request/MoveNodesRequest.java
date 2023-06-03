
package quanta.request;

import java.util.List;
import quanta.request.base.RequestBase;

public class MoveNodesRequest extends RequestBase {
	/* parent under which the nodes will be moved */
	private String targetNodeId;
	private List<String> nodeIds;
	private String location;

	public String getTargetNodeId() {
		return this.targetNodeId;
	}
	
	public List<String> getNodeIds() {
		return this.nodeIds;
	}
	
	public String getLocation() {
		return this.location;
	}
	
	public void setTargetNodeId(final String targetNodeId) {
		this.targetNodeId = targetNodeId;
	}
	
	public void setNodeIds(final List<String> nodeIds) {
		this.nodeIds = nodeIds;
	}
	
	public void setLocation(final String location) {
		this.location = location;
	}

	public MoveNodesRequest() {
	}
}
