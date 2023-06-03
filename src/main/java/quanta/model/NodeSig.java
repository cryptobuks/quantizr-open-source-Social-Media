
package quanta.model;
// A nodeId and the signature of the node
public class NodeSig {
    private String nodeId;
    private String sig;

    public String getNodeId() {
        return this.nodeId;
    }
    
    public String getSig() {
        return this.sig;
    }
    
    public void setNodeId(final String nodeId) {
        this.nodeId = nodeId;
    }

    public void setSig(final String sig) {
        this.sig = sig;
    }
    
    public NodeSig() {
    }
}
