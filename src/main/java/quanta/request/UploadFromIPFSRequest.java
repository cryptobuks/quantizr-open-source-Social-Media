
package quanta.request;

import quanta.request.base.RequestBase;

public class UploadFromIPFSRequest extends RequestBase {
	/* if this is false we store only a link to the file, rather than copying it into our db */
	private boolean pinLocally;
	private String nodeId;
	private String cid;
	private String mime;

	
	public boolean isPinLocally() {
		return this.pinLocally;
	}

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public String getCid() {
		return this.cid;
	}

	
	public String getMime() {
		return this.mime;
	}

	
	public void setPinLocally(final boolean pinLocally) {
		this.pinLocally = pinLocally;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setCid(final String cid) {
		this.cid = cid;
	}

	
	public void setMime(final String mime) {
		this.mime = mime;
	}

	public UploadFromIPFSRequest() {
	}
}
