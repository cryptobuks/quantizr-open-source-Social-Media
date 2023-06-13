package quanta.request;

import quanta.request.base.RequestBase;

public class AppDropRequest extends RequestBase {

    private String data;

    public String getData() {
        return this.data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public AppDropRequest() {}
}
