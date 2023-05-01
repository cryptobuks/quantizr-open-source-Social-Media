package quanta.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

// todo-0: check all constructor calls on all these types of classes
@Data
@NoArgsConstructor
/* Holds a list of data to be pushed down to client for signing */
public class NodeSigPushInfo extends ServerPushInfo {
	private Integer workloadId;
	private List<NodeSigData> listToSign;

	public NodeSigPushInfo(Integer workloadId) {
		super("sigPush");
		this.workloadId = workloadId;
	}
}
