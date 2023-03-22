package quanta.model.ipfs.dag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MerkleLink {
    @JsonProperty("Name")
    private String name;

    @JsonProperty("Hash")
    private String hash;

    @JsonProperty("Size")
    private Integer size;

    @JsonProperty("Cid")
    private MerkleCid cid;
}
