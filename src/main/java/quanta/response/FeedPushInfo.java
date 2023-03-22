package quanta.response;

import quanta.model.NodeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FeedPushInfo extends ServerPushInfo {
	private NodeInfo nodeInfo;

	public FeedPushInfo(NodeInfo nodeInfo) {
		super("feedPush");
		this.nodeInfo = nodeInfo;
	}
}
