package quanta.request;

import quanta.request.base.RequestBase;

public class AddFriendRequest extends RequestBase {

    private String userName;

    public String getUserName() {
        return this.userName;
    }

    public void setUserName(final String userName) {
        this.userName = userName;
    }

    public AddFriendRequest() {}
}
