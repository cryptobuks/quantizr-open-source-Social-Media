package quanta.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
/* Holds a list of data to be pushed down to client for signing */
public class NodeSigPushInfo extends ServerPushInfo {
	private Integer workloadId;
	private List<NodeSigData> listToSign;

	public NodeSigPushInfo() {
		super("sigPush");
	}

	public NodeSigPushInfo(Integer workloadId) {
		this();
		this.workloadId = workloadId;
	}
}
