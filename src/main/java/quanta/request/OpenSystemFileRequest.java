
package quanta.request;

import quanta.request.base.RequestBase;

public class OpenSystemFileRequest extends RequestBase {
	private String fileName;
	
	public String getFileName() {
		return this.fileName;
	}
	
	public void setFileName(final String fileName) {
		this.fileName = fileName;
	}
	
	public OpenSystemFileRequest() {
	}
}
