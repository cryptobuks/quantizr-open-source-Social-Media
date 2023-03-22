package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GraphRequest extends RequestBase {

	/* can be node id or path. server interprets correctly no matter which */
	private String nodeId;

	// optional, to perform search to build a graphical result of that.
	private String searchText;
}
