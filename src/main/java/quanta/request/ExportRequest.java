package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExportRequest extends RequestBase {
	private String nodeId;

	// must be file extension, and selects which type of file to export
	private String exportExt;
	private String fileName;
	private boolean toIpfs;
	private boolean includeToc;
	private boolean largeHtmlFile;
	private boolean jupyterFile;
	private boolean attOneFolder;

	private boolean includeJSON;
	private boolean includeMD;
	private boolean includeHTML;
	private boolean includeIDs;
	private boolean dividerLine;
}
