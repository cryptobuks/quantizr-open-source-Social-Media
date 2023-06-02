
package quanta.request;

import quanta.request.base.RequestBase;

public class GetIPFSFilesRequest extends RequestBase {
    private String folder;

    public String getFolder() {
        return this.folder;
    }

    public void setFolder(final String folder) {
        this.folder = folder;
    }

    public GetIPFSFilesRequest() {
    }
}
