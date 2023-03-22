package quanta.model.ipfs.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
}
