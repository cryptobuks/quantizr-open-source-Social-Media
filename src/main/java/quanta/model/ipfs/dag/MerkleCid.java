package quanta.model.ipfs.dag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MerkleCid {
    @JsonProperty("/")
    private String path;
}
