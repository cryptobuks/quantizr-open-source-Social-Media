
package quanta.request;

import quanta.request.base.RequestBase;

public class RenderDocumentRequest extends RequestBase {
    private String rootId;
    private String startNodeId;
    private boolean includeComments;
    
    public String getRootId() {
        return this.rootId;
    }
    
    public String getStartNodeId() {
        return this.startNodeId;
    }
    
    public boolean isIncludeComments() {
        return this.includeComments;
    }
    
    public void setRootId(final String rootId) {
        this.rootId = rootId;
    }
    
    public void setStartNodeId(final String startNodeId) {
        this.startNodeId = startNodeId;
    }
    
    public void setIncludeComments(final boolean includeComments) {
        this.includeComments = includeComments;
    }

    public RenderDocumentRequest() {
    }
}
