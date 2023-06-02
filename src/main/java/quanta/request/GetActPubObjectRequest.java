
package quanta.request;

import quanta.request.base.RequestBase;

public class GetActPubObjectRequest extends RequestBase {
	private String url;

	
	public String getUrl() {
		return this.url;
	}

	
	public void setUrl(final String url) {
		this.url = url;
	}

	public GetActPubObjectRequest() {
	}
}
