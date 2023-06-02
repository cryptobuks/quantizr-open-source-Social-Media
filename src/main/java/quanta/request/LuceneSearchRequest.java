
package quanta.request;

import quanta.request.base.RequestBase;

public class LuceneSearchRequest extends RequestBase {
    private String nodeId;
    private String text;

    
    public String getNodeId() {
        return this.nodeId;
    }

    
    public String getText() {
        return this.text;
    }

    
    public void setNodeId(final String nodeId) {
        this.nodeId = nodeId;
    }

    
    public void setText(final String text) {
        this.text = text;
    }

    public LuceneSearchRequest() {
    }
}
