
package quanta.model.client;

import java.util.ArrayList;

public class NostrEvent {
    private String id;
    private String sig;
    private String pubkey;
    private Integer kind;
    private String content;
    private ArrayList<ArrayList<String>> tags;
    private Long createdAt;

    
    public String getId() {
        return this.id;
    }

    
    public String getSig() {
        return this.sig;
    }

    
    public String getPubkey() {
        return this.pubkey;
    }

    
    public Integer getKind() {
        return this.kind;
    }

    
    public String getContent() {
        return this.content;
    }

    
    public ArrayList<ArrayList<String>> getTags() {
        return this.tags;
    }

    
    public Long getCreatedAt() {
        return this.createdAt;
    }

    
    public void setId(final String id) {
        this.id = id;
    }

    
    public void setSig(final String sig) {
        this.sig = sig;
    }

    
    public void setPubkey(final String pubkey) {
        this.pubkey = pubkey;
    }

    
    public void setKind(final Integer kind) {
        this.kind = kind;
    }

    
    public void setContent(final String content) {
        this.content = content;
    }

    
    public void setTags(final ArrayList<ArrayList<String>> tags) {
        this.tags = tags;
    }

    
    public void setCreatedAt(final Long createdAt) {
        this.createdAt = createdAt;
    }
    
    public NostrEvent() {
    }
}
