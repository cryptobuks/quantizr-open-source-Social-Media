package quanta.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
/* Holds NodeId and data to be signed by the browser for the node */
public class NodeSigData {
	private String nodeId;
	private String data;

	public NodeSigData(String nodeId, String data) {
		this.nodeId = nodeId;
		this.data = data;
	}
}
