package quanta.response;

import java.util.List;

import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NodeFeedResponse extends ResponseBase {
	private Boolean endReached;

	/* orderablility of children not set in these objects, all will be false */
	private List<NodeInfo> searchResults;

	private List<String> friendHashTags;
}
