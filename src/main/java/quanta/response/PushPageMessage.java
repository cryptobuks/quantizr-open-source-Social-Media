package quanta.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PushPageMessage extends ServerPushInfo {
	private String payload;
	private boolean usePopup;

	public PushPageMessage(String payload, boolean usePopup) {
		super("pushPageMessage");
		this.payload = payload;
		this.usePopup = usePopup;
	}
}
