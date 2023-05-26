package quanta.request;

import quanta.model.NodeInfo;
import quanta.model.client.NostrEventWrapper;
import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SaveNodeRequest extends RequestBase {
	private NodeInfo node;

	// if we're saving a nostr event this will be non-null and mainly used so we can verify it's 
	// signature before saving the OBJECT_ID onto the node.
	private NostrEventWrapper nostrEvent;
	boolean saveToActPub;
}
