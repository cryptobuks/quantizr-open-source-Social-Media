
package quanta.response;

import java.util.List;
import quanta.model.client.MFSDirEntry;
import quanta.response.base.ResponseBase;

public class GetIPFSFilesResponse extends ResponseBase {
	public List<MFSDirEntry> files;
	// returns whatever folder ended up gettin gloaded
	public String folder;
	public String cid;

	
	public List<MFSDirEntry> getFiles() {
		return this.files;
	}

	
	public String getFolder() {
		return this.folder;
	}

	
	public String getCid() {
		return this.cid;
	}

	
	public void setFiles(final List<MFSDirEntry> files) {
		this.files = files;
	}

	
	public void setFolder(final String folder) {
		this.folder = folder;
	}

	
	public void setCid(final String cid) {
		this.cid = cid;
	}

	public GetIPFSFilesResponse() {
	}
}
