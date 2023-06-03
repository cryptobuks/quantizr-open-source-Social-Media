
package quanta.model.client;

import java.util.List;

public class NostrQuery {
    private List<String> authors;
    private List<Integer> kinds;
    private Integer limit;
    private Long since;
    
    public List<String> getAuthors() {
        return this.authors;
    }
    
    public List<Integer> getKinds() {
        return this.kinds;
    }

    public Integer getLimit() {
        return this.limit;
    }
    
    public Long getSince() {
        return this.since;
    }
    
    public void setAuthors(final List<String> authors) {
        this.authors = authors;
    }
    
    public void setKinds(final List<Integer> kinds) {
        this.kinds = kinds;
    }

    public void setLimit(final Integer limit) {
        this.limit = limit;
    }
    
    public void setSince(final Long since) {
        this.since = since;
    }
    
    public NostrQuery() {
    }
}
