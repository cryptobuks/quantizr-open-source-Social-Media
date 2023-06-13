package quanta.request;

import quanta.request.base.RequestBase;

public class BlockUserRequest extends RequestBase {

    private String userName;

    public String getUserName() {
        return this.userName;
    }

    public void setUserName(final String userName) {
        this.userName = userName;
    }

    public BlockUserRequest() {}
}
