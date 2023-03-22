package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RemovePrivilegeRequest extends RequestBase {
	private String nodeId;
	private String principalNodeId;

	/* for now only 'public' is the only option we support */
	private String privilege;
}
