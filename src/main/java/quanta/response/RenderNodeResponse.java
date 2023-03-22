package quanta.response;

import java.util.LinkedList;
import quanta.model.BreadcrumbInfo;
import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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

}
