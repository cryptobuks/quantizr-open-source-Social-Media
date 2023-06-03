
package quanta.request;

import quanta.request.base.RequestBase;

public class SplitNodeRequest extends RequestBase {
	// Nodes can be split right inline or by creating an array of child nodes.
	// INLINE or CHILDREN
	private String splitType;
	private String nodeId;
	private String delimiter;
	
	public String getSplitType() {
		return this.splitType;
	}
	
	public String getNodeId() {
		return this.nodeId;
	}
	
	public String getDelimiter() {
		return this.delimiter;
	}
	
	public void setSplitType(final String splitType) {
		this.splitType = splitType;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setDelimiter(final String delimiter) {
		this.delimiter = delimiter;
	}

	public SplitNodeRequest() {
	}
}
