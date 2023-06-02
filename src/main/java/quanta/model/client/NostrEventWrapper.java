
package quanta.model.client;

public class NostrEventWrapper {
    private NostrEvent event;
    private String nodeId; // quanta nodeId if known, or else null
    private String npub; // if this is a user metadata event we put the npub here.
    private String relays; // if user metadate client will send relays here

    
    public NostrEvent getEvent() {
        return this.event;
    }

    
    public String getNodeId() {
        return this.nodeId;
    }

    
    public String getNpub() {
        return this.npub;
    }

    
    public String getRelays() {
        return this.relays;
    }

    
    public void setEvent(final NostrEvent event) {
        this.event = event;
    }

    
    public void setNodeId(final String nodeId) {
        this.nodeId = nodeId;
    }

    
    public void setNpub(final String npub) {
        this.npub = npub;
    }

    
    public void setRelays(final String relays) {
        this.relays = relays;
    }
    
    public NostrEventWrapper() {
    }
}
