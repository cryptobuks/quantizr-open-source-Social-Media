package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NodeSearchRequest extends RequestBase {

	/* Can be 'curNode' (default, null) or 'allNodes' */
	private String searchRoot;

	/* Zero offset page number. First page is zero */
	private int page;

	/* ascending=asc, descending=desc */
	private String sortDir;

	/* property to sort on */
	private String sortField;

	/* can be node id or path. server interprets correctly no matter which */
	private String nodeId;

	private String searchText;

	private String searchProp;

	// fuzzy means you can get substring searches, where the substring is not on the FIRST characters of
	// a term
	private boolean fuzzy;

	private boolean caseSensitive;

	// special definition name which gives the server a hint about what kind of search this is
	private String searchDefinition;

	private String searchType;

	private String timeRangeType;

	private boolean recursive;

	private boolean requirePriority;

	private boolean requireAttachment;

	// Admin can set this, and it will delete all matches to the search results
	private boolean deleteMatches;
}
