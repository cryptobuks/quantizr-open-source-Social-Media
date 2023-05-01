package quanta.response;

import java.util.List;
import lombok.Data;

// todo-0: check all constructor calls on all these types of classes
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
