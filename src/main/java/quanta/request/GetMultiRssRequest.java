
package quanta.request;

import quanta.request.base.RequestBase;

public class GetMultiRssRequest extends RequestBase {
    private String urls;
    private Integer page;
    
    public String getUrls() {
        return this.urls;
    }
    
    public Integer getPage() {
        return this.page;
    }
    
    public void setUrls(final String urls) {
        this.urls = urls;
    }
    
    public void setPage(final Integer page) {
        this.page = page;
    }

    public GetMultiRssRequest() {
    }
}
