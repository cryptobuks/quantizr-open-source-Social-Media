package quanta.request;

import quanta.request.base.RequestBase;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetThreadViewRequest extends RequestBase {
	private String nodeId;
	private boolean loadOthers;

	// if we're showing history of a Nostr node, and we already know all the nodes (in additio to nodeId) that
	// are in the thread, then we send them up to server in this var.
	private List<String> nostrNodeIds;
}
