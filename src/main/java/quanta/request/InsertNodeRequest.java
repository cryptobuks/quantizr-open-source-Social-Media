package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
}
