
package quanta.request;

import quanta.request.base.RequestBase;

public class GetIPFSContentRequest extends RequestBase {
    // rename this to 'mfsPath'
    private String id;

    
    public String getId() {
        return this.id;
    }

    
    public void setId(final String id) {
        this.id = id;
    }

    public GetIPFSContentRequest() {
    }
}
