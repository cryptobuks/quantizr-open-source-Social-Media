
package quanta.response;

import quanta.response.base.ResponseBase;
import quanta.model.NodeInfo;

public class GetActPubObjectResponse extends ResponseBase {
    private NodeInfo node;

    
    public NodeInfo getNode() {
        return this.node;
    }

    
    public void setNode(final NodeInfo node) {
        this.node = node;
    }

    public GetActPubObjectResponse() {
    }
}
