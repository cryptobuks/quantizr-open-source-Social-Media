package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TransferNodeRequest extends RequestBase {
	private boolean recursive;
	private String nodeId;
	private String fromUser;
	private String toUser;

	// transfer, accept, reject
	private String operation;
}
