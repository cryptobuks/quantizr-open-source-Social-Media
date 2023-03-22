package quanta.model.ipfs.dag;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DagNode {

    // don't need this, make it an Object for now.
    @JsonProperty("Data")
    private Object data;

    @JsonProperty("Links")
    private List<DagLink> links;
}
