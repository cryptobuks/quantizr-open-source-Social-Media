
package quanta.request;

import quanta.request.base.RequestBase;

public class SearchAndReplaceRequest extends RequestBase {
	private boolean recursive;
	private String nodeId;
	private String search;
	private String replace;

	public boolean isRecursive() {
		return this.recursive;
	}
	
	public String getNodeId() {
		return this.nodeId;
	}
	
	public String getSearch() {
		return this.search;
	}

	public String getReplace() {
		return this.replace;
	}
	
	public void setRecursive(final boolean recursive) {
		this.recursive = recursive;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setSearch(final String search) {
		this.search = search;
	}
	
	public void setReplace(final String replace) {
		this.replace = replace;
	}
	
	public SearchAndReplaceRequest() {
	}
}
