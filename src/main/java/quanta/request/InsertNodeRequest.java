
package quanta.request;

import quanta.request.base.RequestBase;
/*
 * Request for inserting new node under the parentId, just below the targetId. TargetId can be null
 * and the new node will just be appended to the end of the child list, or may even be the first
 * (i.e. only) child.
 */
public class InsertNodeRequest extends RequestBase {
	private boolean pendingEdit;
	private String parentId;
	private Long targetOrdinal;
	private String newNodeName;
	private String typeName;
	private String initialValue;
	
	public boolean isPendingEdit() {
		return this.pendingEdit;
	}
	
	public String getParentId() {
		return this.parentId;
	}
	
	public Long getTargetOrdinal() {
		return this.targetOrdinal;
	}
	
	public String getNewNodeName() {
		return this.newNodeName;
	}
	
	public String getTypeName() {
		return this.typeName;
	}
	
	public String getInitialValue() {
		return this.initialValue;
	}
	
	public void setPendingEdit(final boolean pendingEdit) {
		this.pendingEdit = pendingEdit;
	}
	
	public void setParentId(final String parentId) {
		this.parentId = parentId;
	}
	
	public void setTargetOrdinal(final Long targetOrdinal) {
		this.targetOrdinal = targetOrdinal;
	}
	
	public void setNewNodeName(final String newNodeName) {
		this.newNodeName = newNodeName;
	}
	
	public void setTypeName(final String typeName) {
		this.typeName = typeName;
	}
	
	public void setInitialValue(final String initialValue) {
		this.initialValue = initialValue;
	}
	
	public InsertNodeRequest() {
	}
}
