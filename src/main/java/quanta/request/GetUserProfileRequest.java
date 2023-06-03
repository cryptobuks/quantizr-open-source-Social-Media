
package quanta.request;

import quanta.request.base.RequestBase;

public class GetUserProfileRequest extends RequestBase {
    public String userId;
    public String nostrPubKey;
    
    public String getUserId() {
        return this.userId;
    }
    
    public String getNostrPubKey() {
        return this.nostrPubKey;
    }
    
    public void setUserId(final String userId) {
        this.userId = userId;
    }
    
    public void setNostrPubKey(final String nostrPubKey) {
        this.nostrPubKey = nostrPubKey;
    }

    public GetUserProfileRequest() {
    }
}
