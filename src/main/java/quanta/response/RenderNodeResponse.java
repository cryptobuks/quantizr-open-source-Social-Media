
package quanta.response;

import java.util.LinkedList;
import quanta.model.BreadcrumbInfo;
import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;

public class RenderNodeResponse extends ResponseBase {
	/* child ordering flag is set in this node object and is correct */
	private NodeInfo node;
	/*
	 * This holds the actual number of children on the node, independent of how many at a time the
	 * browser is requesting to see per page, and unrelated to size of 'children' list, on this object.
	 */
	private boolean endReached;
	private String noDataResponse;
	private LinkedList<BreadcrumbInfo> breadcrumbs;
	private boolean rssNode;

	
	public NodeInfo getNode() {
		return this.node;
	}

	
	public boolean isEndReached() {
		return this.endReached;
	}

	
	public String getNoDataResponse() {
		return this.noDataResponse;
	}

	
	public LinkedList<BreadcrumbInfo> getBreadcrumbs() {
		return this.breadcrumbs;
	}

	
	public boolean isRssNode() {
		return this.rssNode;
	}

	
	public void setNode(final NodeInfo node) {
		this.node = node;
	}

	
	public void setEndReached(final boolean endReached) {
		this.endReached = endReached;
	}

	
	public void setNoDataResponse(final String noDataResponse) {
		this.noDataResponse = noDataResponse;
	}

	
	public void setBreadcrumbs(final LinkedList<BreadcrumbInfo> breadcrumbs) {
		this.breadcrumbs = breadcrumbs;
	}

	
	public void setRssNode(final boolean rssNode) {
		this.rssNode = rssNode;
	}

	public RenderNodeResponse() {
	}
}
