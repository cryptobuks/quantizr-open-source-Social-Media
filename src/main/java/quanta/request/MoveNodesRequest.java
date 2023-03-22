package quanta.request;

import java.util.List;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MoveNodesRequest extends RequestBase {
	/* parent under which the nodes will be moved */
	private String targetNodeId;

	private List<String> nodeIds;

	private String location;
}
