
package quanta.request;

import quanta.request.base.RequestBase;

public class LoadNodeFromIpfsRequest extends RequestBase {
	private String path;

	public String getPath() {
		return this.path;
	}

	public void setPath(final String path) {
		this.path = path;
	}
	
	public LoadNodeFromIpfsRequest() {
	}
}
