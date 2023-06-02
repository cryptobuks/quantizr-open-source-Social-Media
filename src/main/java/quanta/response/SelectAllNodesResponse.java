
package quanta.response;

import java.util.List;
import quanta.response.base.ResponseBase;

public class SelectAllNodesResponse extends ResponseBase {
    private List<String> nodeIds;

    
    public List<String> getNodeIds() {
        return this.nodeIds;
    }

    
    public void setNodeIds(final List<String> nodeIds) {
        this.nodeIds = nodeIds;
    }

    public SelectAllNodesResponse() {
    }
}
