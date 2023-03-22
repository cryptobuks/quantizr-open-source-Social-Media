package quanta.model;

import lombok.Data;
import lombok.NoArgsConstructor;

// A nodeId and the signature of the node
@Data
@NoArgsConstructor
public class NodeSig {
    private String nodeId;
    private String sig;
}
