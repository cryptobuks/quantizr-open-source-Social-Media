
package quanta.request;

import quanta.request.base.RequestBase;

public class GetFollowersRequest extends RequestBase {
    private int page;
    /*
     * user to get followers of (if this is a foreign user, of course it needs to go thru ActivityPub)
     */
    private String targetUserName;

    
    public int getPage() {
        return this.page;
    }

    
    public String getTargetUserName() {
        return this.targetUserName;
    }

    
    public void setPage(final int page) {
        this.page = page;
    }

    
    public void setTargetUserName(final String targetUserName) {
        this.targetUserName = targetUserName;
    }
    
    public GetFollowersRequest() {
    }
}
