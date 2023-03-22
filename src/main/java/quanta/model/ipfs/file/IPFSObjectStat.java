package quanta.model.ipfs.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
}
