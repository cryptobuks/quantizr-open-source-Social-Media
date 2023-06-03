
package quanta.request;

import quanta.request.base.RequestBase;

public class GetSharedNodesRequest extends RequestBase {
	private int page;
	/* can be node id or path. server interprets correctly no matter which */
	private String nodeId;
	/* can be 'public' to find keys in ACL or else null to find all non-null acls */
	private String shareTarget;
	private String accessOption; // for public can be rd, rw, or null (all)

	public int getPage() {
		return this.page;
	}
	
	public String getNodeId() {
		return this.nodeId;
	}
	
	public String getShareTarget() {
		return this.shareTarget;
	}
	
	public String getAccessOption() {
		return this.accessOption;
	}
	
	public void setPage(final int page) {
		this.page = page;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setShareTarget(final String shareTarget) {
		this.shareTarget = shareTarget;
	}
	
	public void setAccessOption(final String accessOption) {
		this.accessOption = accessOption;
	}
	
	public GetSharedNodesRequest() {
	}
}
