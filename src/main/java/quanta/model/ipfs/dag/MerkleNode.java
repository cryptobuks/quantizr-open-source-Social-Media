
package quanta.model.ipfs.dag;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MerkleNode {
    @JsonProperty("Hash")
    private String hash;
    @JsonProperty("Links")
    private List<MerkleLink> links;
    @JsonIgnore
    private String contentType;

    public String getHash() {
        return this.hash;
    }
    
    public List<MerkleLink> getLinks() {
        return this.links;
    }
    
    public String getContentType() {
        return this.contentType;
    }

    @JsonProperty("Hash")    
    public void setHash(final String hash) {
        this.hash = hash;
    }

    @JsonProperty("Links")
    public void setLinks(final List<MerkleLink> links) {
        this.links = links;
    }

    @JsonIgnore
    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }
    
    public MerkleNode() {
    }
}
