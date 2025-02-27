
package quanta.request;

import quanta.model.NodeInfo;
import quanta.model.client.NostrEventWrapper;
import quanta.request.base.RequestBase;

public class SaveNodeRequest extends RequestBase {
	private NodeInfo node;
	// if we're saving a nostr event this will be non-null and mainly used so we can verify it's 
	// signature before saving the OBJECT_ID onto the node.
	private NostrEventWrapper nostrEvent;
	boolean saveToActPub;

	public NodeInfo getNode() {
		return this.node;
	}
	
	public NostrEventWrapper getNostrEvent() {
		return this.nostrEvent;
	}
	
	public boolean isSaveToActPub() {
		return this.saveToActPub;
	}
	
	public void setNode(final NodeInfo node) {
		this.node = node;
	}
	
	public void setNostrEvent(final NostrEventWrapper nostrEvent) {
		this.nostrEvent = nostrEvent;
	}
	
	public void setSaveToActPub(final boolean saveToActPub) {
		this.saveToActPub = saveToActPub;
	}

	public SaveNodeRequest() {
	}
}
