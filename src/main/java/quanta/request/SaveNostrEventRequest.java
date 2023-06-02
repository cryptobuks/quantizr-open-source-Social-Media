
package quanta.request;

import java.util.List;
import quanta.model.client.NostrEventWrapper;
import quanta.model.client.NostrUserInfo;
import quanta.request.base.RequestBase;

public class SaveNostrEventRequest extends RequestBase {
    public List<NostrEventWrapper> events;
    public List<NostrUserInfo> userInfo;

    
    public List<NostrEventWrapper> getEvents() {
        return this.events;
    }

    
    public List<NostrUserInfo> getUserInfo() {
        return this.userInfo;
    }

    
    public void setEvents(final List<NostrEventWrapper> events) {
        this.events = events;
    }

    
    public void setUserInfo(final List<NostrUserInfo> userInfo) {
        this.userInfo = userInfo;
    }

    public SaveNostrEventRequest() {
    }
}
