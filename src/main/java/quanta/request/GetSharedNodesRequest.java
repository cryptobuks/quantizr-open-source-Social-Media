package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetSharedNodesRequest extends RequestBase {
	private int page;

	/* can be node id or path. server interprets correctly no matter which */
	private String nodeId;

	/* can be 'public' to find keys in ACL or else null to find all non-null acls */
	private String shareTarget;

	private String accessOption; // for public can be rd, rw, or null (all)
}
