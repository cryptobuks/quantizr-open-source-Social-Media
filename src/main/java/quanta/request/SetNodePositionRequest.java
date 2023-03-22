package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SetNodePositionRequest extends RequestBase {
	// node to be moved (id or path)
	private String nodeId;

	// targetName can be: up, down, top, bottom
	private String targetName;
}
