
package quanta.response;

public class PushPageMessage extends ServerPushInfo {
	private String payload;
	private boolean usePopup;

	public PushPageMessage(String payload, boolean usePopup) {
		super("pushPageMessage");
		this.payload = payload;
		this.usePopup = usePopup;
	}

	
	public String getPayload() {
		return this.payload;
	}

	
	public boolean isUsePopup() {
		return this.usePopup;
	}

	
	public void setPayload(final String payload) {
		this.payload = payload;
	}

	
	public void setUsePopup(final boolean usePopup) {
		this.usePopup = usePopup;
	}

	public PushPageMessage() {
	}
}
