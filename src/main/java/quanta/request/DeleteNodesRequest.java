
package quanta.request;

import java.util.List;
import quanta.request.base.RequestBase;

public class DeleteNodesRequest extends RequestBase {
	private List<String> nodeIds;
	private boolean childrenOnly;
	private boolean bulkDelete;

	public List<String> getNodeIds() {
		return this.nodeIds;
	}
	
	public boolean isChildrenOnly() {
		return this.childrenOnly;
	}
	
	public boolean isBulkDelete() {
		return this.bulkDelete;
	}
	
	public void setNodeIds(final List<String> nodeIds) {
		this.nodeIds = nodeIds;
	}
	
	public void setChildrenOnly(final boolean childrenOnly) {
		this.childrenOnly = childrenOnly;
	}
	
	public void setBulkDelete(final boolean bulkDelete) {
		this.bulkDelete = bulkDelete;
	}

	public DeleteNodesRequest() {
	}
}
