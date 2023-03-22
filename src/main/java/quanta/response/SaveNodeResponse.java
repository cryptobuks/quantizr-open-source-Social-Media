package quanta.response;

import java.util.List;

import quanta.model.AccessControlInfo;
import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SaveNodeResponse extends ResponseBase {
	private NodeInfo node;

	/*
	 * In cases where the updated node is adding encryption we need to send back all the principalIds
	 * (userNodeIds actually) so the client can generate keys for all of them to send back up to allow
	 * access by these shared users. Unless the node is being encrypted these aclEntries will be null
	 */
	private List<AccessControlInfo> aclEntries;
}
