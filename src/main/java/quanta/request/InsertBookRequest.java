
package quanta.request;

import quanta.request.base.RequestBase;

public class InsertBookRequest extends RequestBase {
	private String nodeId;
	private String bookName;
	/* set to true to only insert a portion of the entire book */
	private Boolean truncated;
	
	public String getNodeId() {
		return this.nodeId;
	}
	
	public String getBookName() {
		return this.bookName;
	}
	
	public Boolean getTruncated() {
		return this.truncated;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setBookName(final String bookName) {
		this.bookName = bookName;
	}
	
	public void setTruncated(final Boolean truncated) {
		this.truncated = truncated;
	}
	
	public InsertBookRequest() {
	}
}
