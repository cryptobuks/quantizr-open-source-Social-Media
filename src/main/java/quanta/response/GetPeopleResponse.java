
package quanta.response;

import java.util.LinkedList;
import java.util.List;
import quanta.response.base.ResponseBase;

public class GetPeopleResponse extends ResponseBase {
    private FriendInfo nodeOwner;
    private List<FriendInfo> people;
    private LinkedList<String> friendHashTags;

    
    public FriendInfo getNodeOwner() {
        return this.nodeOwner;
    }

    
    public List<FriendInfo> getPeople() {
        return this.people;
    }

    
    public LinkedList<String> getFriendHashTags() {
        return this.friendHashTags;
    }

    
    public void setNodeOwner(final FriendInfo nodeOwner) {
        this.nodeOwner = nodeOwner;
    }

    
    public void setPeople(final List<FriendInfo> people) {
        this.people = people;
    }

    
    public void setFriendHashTags(final LinkedList<String> friendHashTags) {
        this.friendHashTags = friendHashTags;
    }

    public GetPeopleResponse() {
    }
}
