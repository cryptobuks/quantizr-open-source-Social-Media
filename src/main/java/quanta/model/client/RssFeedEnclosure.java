
package quanta.model.client;

public class RssFeedEnclosure {
    private String type;
    private String url;

    
    public String getType() {
        return this.type;
    }

    
    public String getUrl() {
        return this.url;
    }

    
    public void setType(final String type) {
        this.type = type;
    }

    
    public void setUrl(final String url) {
        this.url = url;
    }
    
    public RssFeedEnclosure() {
    }
}
