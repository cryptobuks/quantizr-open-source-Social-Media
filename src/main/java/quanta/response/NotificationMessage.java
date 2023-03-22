package quanta.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
}
