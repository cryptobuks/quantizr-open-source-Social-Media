package quanta.model.ipfs.file;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IPFSObjectStat {

    @JsonProperty("BlockSize")
    private Integer blockSize;

    @JsonProperty("CumulativeSize")
    private Integer cumulativeSize;

    @JsonProperty("DataSize")
    private Integer dataSize;

    @JsonProperty("Hash")
    private String hash;

    @JsonProperty("LinksSize")
    private Integer linksSize;

    @JsonProperty("NumLinks")
    private Integer numLinks;

    public Integer getBlockSize() {
        return this.blockSize;
    }

    public Integer getCumulativeSize() {
        return this.cumulativeSize;
    }

    public Integer getDataSize() {
        return this.dataSize;
    }

    public String getHash() {
        return this.hash;
    }

    public Integer getLinksSize() {
        return this.linksSize;
    }

    public Integer getNumLinks() {
        return this.numLinks;
    }

    @JsonProperty("BlockSize")
    public void setBlockSize(final Integer blockSize) {
        this.blockSize = blockSize;
    }

    @JsonProperty("CumulativeSize")
    public void setCumulativeSize(final Integer cumulativeSize) {
        this.cumulativeSize = cumulativeSize;
    }

    @JsonProperty("DataSize")
    public void setDataSize(final Integer dataSize) {
        this.dataSize = dataSize;
    }

    @JsonProperty("Hash")
    public void setHash(final String hash) {
        this.hash = hash;
    }

    @JsonProperty("LinksSize")
    public void setLinksSize(final Integer linksSize) {
        this.linksSize = linksSize;
    }

    @JsonProperty("NumLinks")
    public void setNumLinks(final Integer numLinks) {
        this.numLinks = numLinks;
    }

    public IPFSObjectStat() {}
}
