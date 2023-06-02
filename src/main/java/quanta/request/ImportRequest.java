
package quanta.request;

import quanta.request.base.RequestBase;

public class ImportRequest extends RequestBase {
	private String nodeId;
	/*
	 * short file name (i.e. not including folder or extension) of target file to be imported from. It's
	 * expected to be in the folder specified by adminDataFolder application property.
	 */
	private String sourceFileName;

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public String getSourceFileName() {
		return this.sourceFileName;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setSourceFileName(final String sourceFileName) {
		this.sourceFileName = sourceFileName;
	}

	public ImportRequest() {
	}
}
