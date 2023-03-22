package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SplitNodeRequest extends RequestBase {
	// Nodes can be split right inline or by creating an array of child nodes.
	// INLINE or CHILDREN
	private String splitType;

	private String nodeId;
	private String delimiter;
}
