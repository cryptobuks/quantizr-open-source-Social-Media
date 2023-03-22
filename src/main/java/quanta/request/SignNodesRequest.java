package quanta.request;

import java.util.List;
import quanta.request.base.RequestBase;
import quanta.response.NodeSigData;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
/*
 * In this reply all the 'data' in each NodeSigData is the signature, and not the data to be signed
 */
public class SignNodesRequest extends RequestBase {
    private Integer workloadId;
    private List<NodeSigData> listToSign;
}
