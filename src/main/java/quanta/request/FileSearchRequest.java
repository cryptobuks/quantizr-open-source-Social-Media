package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FileSearchRequest extends RequestBase {
	private String searchText;
	private boolean reindex;

	/* Node user has selected when running the command */
	private String nodeId;
}
