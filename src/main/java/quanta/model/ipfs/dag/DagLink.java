
package quanta.model.ipfs.dag;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DagLink {
    @JsonProperty("Name")
    private String name;
    @JsonProperty("Hash")
    private MerkleCid hash;
    @JsonProperty("Tsize")
    private Integer tsize;

    public String getName() {
        return this.name;
    }
    
    public MerkleCid getHash() {
        return this.hash;
    }
    
    public Integer getTsize() {
        return this.tsize;
    }

    @JsonProperty("Name")
    public void setName(final String name) {
        this.name = name;
    }

    @JsonProperty("Hash")
    public void setHash(final MerkleCid hash) {
        this.hash = hash;
    }

    @JsonProperty("Tsize")
    public void setTsize(final Integer tsize) {
        this.tsize = tsize;
    }
    
    public DagLink() {
    }
}
