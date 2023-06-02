
package quanta.request;

import quanta.request.base.RequestBase;

public class GetPeopleRequest extends RequestBase {
    // if nodeId is non-null we return only the info for the users associated with that node, whichi
    // means everyone mentioned in the text plus, everyone in the shares.
    private String nodeId;
    private String type; // friends | blocks
    private String subType; // null | nostr

    
    public String getNodeId() {
        return this.nodeId;
    }

    
    public String getType() {
        return this.type;
    }

    
    public String getSubType() {
        return this.subType;
    }

    
    public void setNodeId(final String nodeId) {
        this.nodeId = nodeId;
    }

    
    public void setType(final String type) {
        this.type = type;
    }

    
    public void setSubType(final String subType) {
        this.subType = subType;
    }
    
    public GetPeopleRequest() {
    }
}
