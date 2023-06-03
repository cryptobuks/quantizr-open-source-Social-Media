
package quanta.response;
/* Holds NodeId and data to be signed by the browser for the node */
public class NodeSigData {
	private String nodeId;
	private String data;

	public NodeSigData(String nodeId, String data) {
		this.nodeId = nodeId;
		this.data = data;
	}
	
	public String getNodeId() {
		return this.nodeId;
	}
	
	public String getData() {
		return this.data;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setData(final String data) {
		this.data = data;
	}

	public NodeSigData() {
	}
}
