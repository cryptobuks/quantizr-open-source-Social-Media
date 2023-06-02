
package quanta.model.client;

public class Bookmark {
    private String name;
    private String id;
    private String selfId;

    
    public String getName() {
        return this.name;
    }

    
    public String getId() {
        return this.id;
    }

    
    public String getSelfId() {
        return this.selfId;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setId(final String id) {
        this.id = id;
    }

    
    public void setSelfId(final String selfId) {
        this.selfId = selfId;
    }

    public Bookmark() {
    }
}
