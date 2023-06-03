
package quanta.response;

import java.util.List;
import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;

public class GetThreadViewResponse extends ResponseBase {
    private List<NodeInfo> nodes;
    private boolean topReached;
    // If user attempted to get a ThreadView for a node that's a Nostr node and the server
    // (i.e. database) didn't have enough information we can optionally send back as much of the thread
    // history as we WERE able to get from the server, and then send back nostrDeadEnd==true so the client
    // can start traversing up the tree from there.
    private boolean nostrDeadEnd;

    public List<NodeInfo> getNodes() {
        return this.nodes;
    }
    
    public boolean isTopReached() {
        return this.topReached;
    }
    
    public boolean isNostrDeadEnd() {
        return this.nostrDeadEnd;
    }
    
    public void setNodes(final List<NodeInfo> nodes) {
        this.nodes = nodes;
    }
    
    public void setTopReached(final boolean topReached) {
        this.topReached = topReached;
    }
    
    public void setNostrDeadEnd(final boolean nostrDeadEnd) {
        this.nostrDeadEnd = nostrDeadEnd;
    }

    public GetThreadViewResponse() {
    }
}
