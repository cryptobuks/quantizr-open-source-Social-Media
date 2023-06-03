
package quanta.model.ipfs.file;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IPFSDirStat {
    @JsonProperty("Hash")
    private String hash;
    @JsonProperty("Size")
    private Integer size;
    @JsonProperty("CumulativeSize")
    private Integer cumulativeSize;
    @JsonProperty("Blocks")
    private Integer blocks;
    @JsonProperty("Type")
    private String type;

    public String getHash() {
        return this.hash;
    }
    
    public Integer getSize() {
        return this.size;
    }
    
    public Integer getCumulativeSize() {
        return this.cumulativeSize;
    }

    public Integer getBlocks() {
        return this.blocks;
    }
    
    public String getType() {
        return this.type;
    }

    @JsonProperty("Hash")    
    public void setHash(final String hash) {
        this.hash = hash;
    }

    @JsonProperty("Size")
    public void setSize(final Integer size) {
        this.size = size;
    }

    @JsonProperty("CumulativeSize")
    public void setCumulativeSize(final Integer cumulativeSize) {
        this.cumulativeSize = cumulativeSize;
    }

    @JsonProperty("Blocks")
    public void setBlocks(final Integer blocks) {
        this.blocks = blocks;
    }

    @JsonProperty("Type")
    public void setType(final String type) {
        this.type = type;
    }

    public IPFSDirStat() {
    }
}
