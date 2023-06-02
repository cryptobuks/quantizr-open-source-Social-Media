
package quanta.request;

import quanta.request.base.RequestBase;

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

	
	public String getSearchRoot() {
		return this.searchRoot;
	}

	
	public int getPage() {
		return this.page;
	}

	
	public String getSortDir() {
		return this.sortDir;
	}

	
	public String getSortField() {
		return this.sortField;
	}

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public String getSearchText() {
		return this.searchText;
	}

	
	public String getSearchProp() {
		return this.searchProp;
	}

	
	public boolean isFuzzy() {
		return this.fuzzy;
	}

	
	public boolean isCaseSensitive() {
		return this.caseSensitive;
	}

	
	public String getSearchDefinition() {
		return this.searchDefinition;
	}

	
	public String getSearchType() {
		return this.searchType;
	}

	
	public String getTimeRangeType() {
		return this.timeRangeType;
	}

	
	public boolean isRecursive() {
		return this.recursive;
	}

	
	public boolean isRequirePriority() {
		return this.requirePriority;
	}

	
	public boolean isRequireAttachment() {
		return this.requireAttachment;
	}

	
	public boolean isDeleteMatches() {
		return this.deleteMatches;
	}

	
	public void setSearchRoot(final String searchRoot) {
		this.searchRoot = searchRoot;
	}

	
	public void setPage(final int page) {
		this.page = page;
	}

	
	public void setSortDir(final String sortDir) {
		this.sortDir = sortDir;
	}

	
	public void setSortField(final String sortField) {
		this.sortField = sortField;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setSearchText(final String searchText) {
		this.searchText = searchText;
	}

	
	public void setSearchProp(final String searchProp) {
		this.searchProp = searchProp;
	}

	
	public void setFuzzy(final boolean fuzzy) {
		this.fuzzy = fuzzy;
	}

	
	public void setCaseSensitive(final boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
	}

	
	public void setSearchDefinition(final String searchDefinition) {
		this.searchDefinition = searchDefinition;
	}

	
	public void setSearchType(final String searchType) {
		this.searchType = searchType;
	}

	
	public void setTimeRangeType(final String timeRangeType) {
		this.timeRangeType = timeRangeType;
	}

	
	public void setRecursive(final boolean recursive) {
		this.recursive = recursive;
	}

	
	public void setRequirePriority(final boolean requirePriority) {
		this.requirePriority = requirePriority;
	}

	
	public void setRequireAttachment(final boolean requireAttachment) {
		this.requireAttachment = requireAttachment;
	}

	
	public void setDeleteMatches(final boolean deleteMatches) {
		this.deleteMatches = deleteMatches;
	}

	public NodeSearchRequest() {
	}
}
