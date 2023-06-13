package quanta.model.ipfs.dag;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MerkleLink {

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Hash")
    private String hash;

    @JsonProperty("Size")
    private Integer size;

    @JsonProperty("Cid")
    private MerkleCid cid;

    public String getName() {
        return this.name;
    }

    public String getHash() {
        return this.hash;
    }

    public Integer getSize() {
        return this.size;
    }

    public MerkleCid getCid() {
        return this.cid;
    }

    @JsonProperty("Name")
    public void setName(final String name) {
        this.name = name;
    }

    @JsonProperty("Hash")
    public void setHash(final String hash) {
        this.hash = hash;
    }

    @JsonProperty("Size")
    public void setSize(final Integer size) {
        this.size = size;
    }

    @JsonProperty("Cid")
    public void setCid(final MerkleCid cid) {
        this.cid = cid;
    }

    public MerkleLink() {}
}
