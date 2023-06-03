
package quanta.request;

import quanta.request.base.RequestBase;

public class DeleteAttachmentRequest extends RequestBase {
	private String nodeId;
	// comma delimited list of names of attachments to delete (the map keys)
	private String attName;
	
	public String getNodeId() {
		return this.nodeId;
	}

	public String getAttName() {
		return this.attName;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setAttName(final String attName) {
		this.attName = attName;
	}

	public DeleteAttachmentRequest() {
	}
}
