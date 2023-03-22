package quanta.model.ipfs.dag;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MerkleNode {

    @JsonProperty("Hash")
    private String hash;

    @JsonProperty("Links")
    private List<MerkleLink> links;

    @JsonIgnore
    private String contentType;
}
