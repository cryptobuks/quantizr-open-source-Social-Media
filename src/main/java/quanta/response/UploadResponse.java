
package quanta.response;

import java.util.List;
import quanta.response.base.ResponseBase;

public class UploadResponse extends ResponseBase {
	private List<String> payloads;

	public List<String> getPayloads() {
		return this.payloads;
	}

	public void setPayloads(final List<String> payloads) {
		this.payloads = payloads;
	}
	
	public UploadResponse() {
	}
}
