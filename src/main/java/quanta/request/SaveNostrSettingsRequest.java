
package quanta.request;

import quanta.request.base.RequestBase;

public class SaveNostrSettingsRequest extends RequestBase {
    // whose identity we're updating (can be currently logged in user (sent as null) or a Friend (sent as Friend Node Id)
    public String target;
    public String key;
    // newline delimited list of relays
    public String relays;
    
    public String getTarget() {
        return this.target;
    }
    
    public String getKey() {
        return this.key;
    }
    
    public String getRelays() {
        return this.relays;
    }
    
    public void setTarget(final String target) {
        this.target = target;
    }
    
    public void setKey(final String key) {
        this.key = key;
    }
    
    public void setRelays(final String relays) {
        this.relays = relays;
    }

    public SaveNostrSettingsRequest() {
    }
}
