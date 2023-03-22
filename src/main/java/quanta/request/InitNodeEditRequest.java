package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InitNodeEditRequest extends RequestBase {
	private String nodeId;

	// if true, this indicates that the 'nodeId' is the ID of a User's Node, and the caller
	// is wanting to start editing his "Friend Node" representing him following said user.
	private Boolean editMyFriendNode;
}
