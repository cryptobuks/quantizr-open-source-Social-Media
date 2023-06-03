
package quanta.response;

import quanta.response.base.ResponseBase;

public class GetUserAccountInfoResponse extends ResponseBase {
    private Integer binTotal;
    private Integer binQuota;

    public Integer getBinTotal() {
        return this.binTotal;
    }
    
    public Integer getBinQuota() {
        return this.binQuota;
    }
    
    public void setBinTotal(final Integer binTotal) {
        this.binTotal = binTotal;
    }
    
    public void setBinQuota(final Integer binQuota) {
        this.binQuota = binQuota;
    }

    public GetUserAccountInfoResponse() {
    }
}
