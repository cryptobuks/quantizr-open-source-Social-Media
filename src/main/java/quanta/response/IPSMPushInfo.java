
package quanta.response;

public class IPSMPushInfo extends ServerPushInfo {
	private String payload;

	public IPSMPushInfo(String payload) {
		super("ipsmPush");
		this.payload = payload;
	}
	
	public String getPayload() {
		return this.payload;
	}
	
	public void setPayload(final String payload) {
		this.payload = payload;
	}

	public IPSMPushInfo() {
	}
}
