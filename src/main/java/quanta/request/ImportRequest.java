package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ImportRequest extends RequestBase {
	private String nodeId;

	/*
	 * short file name (i.e. not including folder or extension) of target file to be imported from. It's
	 * expected to be in the folder specified by adminDataFolder application property.
	 */
	private String sourceFileName;
}
