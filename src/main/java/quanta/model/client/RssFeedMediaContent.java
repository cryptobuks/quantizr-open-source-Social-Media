
package quanta.model.client;

public class RssFeedMediaContent {
    private String type;
    private String url;
    private String medium;

    public String getType() {
        return this.type;
    }
    
    public String getUrl() {
        return this.url;
    }
    
    public String getMedium() {
        return this.medium;
    }
    
    public void setType(final String type) {
        this.type = type;
    }
    
    public void setUrl(final String url) {
        this.url = url;
    }
    
    public void setMedium(final String medium) {
        this.medium = medium;
    }
    
    public RssFeedMediaContent() {
    }
}
