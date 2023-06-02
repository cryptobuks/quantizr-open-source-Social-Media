
package quanta.request;

import java.util.List;
import quanta.request.base.RequestBase;
import quanta.response.NodeSigData;
/*
 * In this reply all the 'data' in each NodeSigData is the signature, and not the data to be signed
 */
public class SignNodesRequest extends RequestBase {
    private Integer workloadId;
    private List<NodeSigData> listToSign;

    
    public Integer getWorkloadId() {
        return this.workloadId;
    }

    
    public List<NodeSigData> getListToSign() {
        return this.listToSign;
    }

    
    public void setWorkloadId(final Integer workloadId) {
        this.workloadId = workloadId;
    }

    
    public void setListToSign(final List<NodeSigData> listToSign) {
        this.listToSign = listToSign;
    }

    public SignNodesRequest() {
    }
}
