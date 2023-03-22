package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LinkNodesRequest extends RequestBase {
	private String sourceNodeId;
	private String targetNodeId;
	private String name;
	private String type; // forward, bidirectional

}
