package quanta.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IPSMPushInfo extends ServerPushInfo {
	private String payload;

	public IPSMPushInfo(String payload) {
		super("ipsmPush");
		this.payload = payload;
	}
}
