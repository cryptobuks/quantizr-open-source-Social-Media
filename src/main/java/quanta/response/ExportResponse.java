
package quanta.response;

import quanta.response.base.ResponseBase;

public class ExportResponse extends ResponseBase {
	private String ipfsCid;
	private String ipfsMime;
	private String fileName;
	
	public String getIpfsCid() {
		return this.ipfsCid;
	}
	
	public String getIpfsMime() {
		return this.ipfsMime;
	}
	
	public String getFileName() {
		return this.fileName;
	}
	
	public void setIpfsCid(final String ipfsCid) {
		this.ipfsCid = ipfsCid;
	}

	public void setIpfsMime(final String ipfsMime) {
		this.ipfsMime = ipfsMime;
	}
	
	public void setFileName(final String fileName) {
		this.fileName = fileName;
	}

	public ExportResponse() {
	}
}
