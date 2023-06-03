
package quanta.response;

public class NotificationMessage extends ServerPushInfo {
	private String nodeId;
	private String fromUser;
	private String message;

	public NotificationMessage(String type, String nodeId, String message, String fromUser) {
		super(type);
		this.nodeId = nodeId;
		this.message = message;
		this.fromUser = fromUser;
	}
	
	public String getNodeId() {
		return this.nodeId;
	}
	
	public String getFromUser() {
		return this.fromUser;
	}
	
	public String getMessage() {
		return this.message;
	}
	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}
	
	public void setFromUser(final String fromUser) {
		this.fromUser = fromUser;
	}
	
	public void setMessage(final String message) {
		this.message = message;
	}

	public NotificationMessage() {
	}
}
