
package quanta.model.client;

public class APTag {
    private String name;
    private String href;
    private String type;

    public String getName() {
        return this.name;
    }

    public String getHref() {
        return this.href;
    }

    public String getType() {
        return this.type;
    }
    
    public void setName(final String name) {
        this.name = name;
    }

    public void setHref(final String href) {
        this.href = href;
    }

    public void setType(final String type) {
        this.type = type;
    }
    
    public APTag() {
    }
}
