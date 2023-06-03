
package quanta.request;

import quanta.request.base.RequestBase;

public class GetConfigRequest extends RequestBase {
    private String appGuid;

    public String getAppGuid() {
        return this.appGuid;
    }

    public void setAppGuid(final String appGuid) {
        this.appGuid = appGuid;
    }

    public GetConfigRequest() {
    }
}
