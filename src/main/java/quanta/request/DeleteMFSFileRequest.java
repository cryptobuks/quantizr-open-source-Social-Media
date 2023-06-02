
package quanta.request;

import quanta.request.base.RequestBase;

public class DeleteMFSFileRequest extends RequestBase {
    private String item;

    
    public String getItem() {
        return this.item;
    }

    
    public void setItem(final String item) {
        this.item = item;
    }

    public DeleteMFSFileRequest() {
    }
}
