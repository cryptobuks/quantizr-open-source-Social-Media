
package quanta.request;

import quanta.request.base.RequestBase;

public class RenderDocumentRequest extends RequestBase {
    private String rootId;
    private boolean includeComments;
    
    public String getRootId() {
        return this.rootId;
    }
    
    public boolean isIncludeComments() {
        return this.includeComments;
    }
    
    public void setRootId(final String rootId) {
        this.rootId = rootId;
    }
    
    public void setIncludeComments(final boolean includeComments) {
        this.includeComments = includeComments;
    }

    public RenderDocumentRequest() {
    }
}
