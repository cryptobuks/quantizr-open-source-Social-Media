
package quanta.request;

import quanta.request.base.RequestBase;

public class LuceneIndexRequest extends RequestBase {
    private String nodeId;
    private String path;

    public String getNodeId() {
        return this.nodeId;
    }

    public String getPath() {
        return this.path;
    }

    public void setNodeId(final String nodeId) {
        this.nodeId = nodeId;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public LuceneIndexRequest() {
    }
}
