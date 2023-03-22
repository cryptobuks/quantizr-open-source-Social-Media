package quanta.response;

import quanta.model.NodeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NodeEditedPushInfo extends ServerPushInfo {
	private NodeInfo nodeInfo;

	public NodeEditedPushInfo(NodeInfo nodeInfo) {
		super("nodeEdited");
		this.nodeInfo = nodeInfo;
	}
}
