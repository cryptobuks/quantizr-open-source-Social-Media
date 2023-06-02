
package quanta.request;

import quanta.request.base.RequestBase;

public class ExportRequest extends RequestBase {
	private String nodeId;
	// must be file extension, and selects which type of file to export
	private String exportExt;
	private String fileName;
	private boolean toIpfs;
	private boolean includeToc;
	private boolean attOneFolder;
	private boolean includeJSON;
	private boolean includeMD;
	private boolean includeHTML;
	private boolean includeJypyter;
	private boolean includeIDs;
	private boolean dividerLine;
	private boolean updateHeadings;

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public String getExportExt() {
		return this.exportExt;
	}

	
	public String getFileName() {
		return this.fileName;
	}

	
	public boolean isToIpfs() {
		return this.toIpfs;
	}

	
	public boolean isIncludeToc() {
		return this.includeToc;
	}

	
	public boolean isAttOneFolder() {
		return this.attOneFolder;
	}

	
	public boolean isIncludeJSON() {
		return this.includeJSON;
	}

	
	public boolean isIncludeMD() {
		return this.includeMD;
	}

	
	public boolean isIncludeHTML() {
		return this.includeHTML;
	}

	
	public boolean isIncludeJypyter() {
		return this.includeJypyter;
	}

	
	public boolean isIncludeIDs() {
		return this.includeIDs;
	}

	
	public boolean isDividerLine() {
		return this.dividerLine;
	}

	
	public boolean isUpdateHeadings() {
		return this.updateHeadings;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setExportExt(final String exportExt) {
		this.exportExt = exportExt;
	}

	
	public void setFileName(final String fileName) {
		this.fileName = fileName;
	}

	
	public void setToIpfs(final boolean toIpfs) {
		this.toIpfs = toIpfs;
	}

	
	public void setIncludeToc(final boolean includeToc) {
		this.includeToc = includeToc;
	}

	
	public void setAttOneFolder(final boolean attOneFolder) {
		this.attOneFolder = attOneFolder;
	}

	
	public void setIncludeJSON(final boolean includeJSON) {
		this.includeJSON = includeJSON;
	}

	
	public void setIncludeMD(final boolean includeMD) {
		this.includeMD = includeMD;
	}

	
	public void setIncludeHTML(final boolean includeHTML) {
		this.includeHTML = includeHTML;
	}

	
	public void setIncludeJypyter(final boolean includeJypyter) {
		this.includeJypyter = includeJypyter;
	}

	
	public void setIncludeIDs(final boolean includeIDs) {
		this.includeIDs = includeIDs;
	}

	
	public void setDividerLine(final boolean dividerLine) {
		this.dividerLine = dividerLine;
	}

	
	public void setUpdateHeadings(final boolean updateHeadings) {
		this.updateHeadings = updateHeadings;
	}

	public ExportRequest() {
	}
}
