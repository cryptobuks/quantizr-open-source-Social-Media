package quanta.model.ipfs.dag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DagLink {
    @JsonProperty("Name")
    private String name;

    @JsonProperty("Hash")
    private MerkleCid hash;

    @JsonProperty("Tsize")
    private Integer tsize;
}
