
package quanta.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Models UserPreferences
 */
public class UserPreferences {
	private boolean editMode;
	private boolean showMetaData;
	private boolean nsfw;
	private boolean showProps;
	private boolean autoRefreshFeed; // #add-prop
	private boolean showParents;
	private boolean showReplies;
	private boolean rssHeadlinesOnly;
	// valid Range = 4 thru 8, inclusive.
	private long mainPanelCols = 6;
	// not persisted to DB yet. ipsm was just an experiment using IPFSSubPub for messaging
	@JsonProperty(required = false)
	private boolean enableIPSM;
	@JsonProperty(required = false)
	private long maxUploadFileSize;

	@JsonProperty(required = false)
	public long getMaxUploadFileSize() {
		return maxUploadFileSize;
	}

	@JsonProperty(required = false)
	public void setMaxUploadFileSize(long maxUploadFileSize) {
		this.maxUploadFileSize = maxUploadFileSize;
	}

	@JsonProperty(required = false)
	public long getMainPanelCols() {
		return mainPanelCols;
	}

	@JsonProperty(required = false)
	public void setMainPanelCols(long mainPanelCols) {
		this.mainPanelCols = mainPanelCols;
	}

	public boolean isEditMode() {
		return this.editMode;
	}
	
	public boolean isShowMetaData() {
		return this.showMetaData;
	}

	public boolean isNsfw() {
		return this.nsfw;
	}

	public boolean isShowProps() {
		return this.showProps;
	}
	
	public boolean isAutoRefreshFeed() {
		return this.autoRefreshFeed;
	}
	
	public boolean isShowParents() {
		return this.showParents;
	}
	
	public boolean isShowReplies() {
		return this.showReplies;
	}
	
	public boolean isRssHeadlinesOnly() {
		return this.rssHeadlinesOnly;
	}

	public boolean isEnableIPSM() {
		return this.enableIPSM;
	}

	public void setEditMode(final boolean editMode) {
		this.editMode = editMode;
	}
	
	public void setShowMetaData(final boolean showMetaData) {
		this.showMetaData = showMetaData;
	}

	public void setNsfw(final boolean nsfw) {
		this.nsfw = nsfw;
	}
	
	public void setShowProps(final boolean showProps) {
		this.showProps = showProps;
	}

	public void setAutoRefreshFeed(final boolean autoRefreshFeed) {
		this.autoRefreshFeed = autoRefreshFeed;
	}

	public void setShowParents(final boolean showParents) {
		this.showParents = showParents;
	}

	public void setShowReplies(final boolean showReplies) {
		this.showReplies = showReplies;
	}
	
	public void setRssHeadlinesOnly(final boolean rssHeadlinesOnly) {
		this.rssHeadlinesOnly = rssHeadlinesOnly;
	}

	@JsonProperty(required = false)
	public void setEnableIPSM(final boolean enableIPSM) {
		this.enableIPSM = enableIPSM;
	}

	public UserPreferences() {
	}
}
