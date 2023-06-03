
package quanta.request;

import quanta.request.base.RequestBase;

public class LinkNodesRequest extends RequestBase {
	private String sourceNodeId;
	private String targetNodeId;
	private String name;
	private String type; // forward, bidirectional

	public String getSourceNodeId() {
		return this.sourceNodeId;
	}
	
	public String getTargetNodeId() {
		return this.targetNodeId;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getType() {
		return this.type;
	}
	
	public void setSourceNodeId(final String sourceNodeId) {
		this.sourceNodeId = sourceNodeId;
	}
	
	public void setTargetNodeId(final String targetNodeId) {
		this.targetNodeId = targetNodeId;
	}
	
	public void setName(final String name) {
		this.name = name;
	}

	public void setType(final String type) {
		this.type = type;
	}

	public LinkNodesRequest() {
	}
}
