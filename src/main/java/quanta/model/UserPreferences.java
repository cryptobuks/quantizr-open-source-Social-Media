package quanta.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Models UserPreferences
 */
public class UserPreferences {
	private boolean editMode;
	private boolean showMetaData;
	private boolean showParents;

	private boolean rssHeadlinesOnly;
	private long mainPanelCols = 5;

	// not persisted to DB yet. ipsm was just an experiment using IPFSSubPub for messaging
	@JsonProperty(required = false)
	private boolean enableIPSM;

	@JsonProperty(required = false)
	private long maxUploadFileSize;

	public boolean isEnableIPSM() {
		return enableIPSM;
	}

	public void setEnableIPSM(boolean enableIPSM) {
		this.enableIPSM = enableIPSM;
	}

	public boolean isRssHeadlinesOnly() {
		return rssHeadlinesOnly;
	}

	public void setRssHeadlinesOnly(boolean rssHeadlinesOnly) {
		this.rssHeadlinesOnly = rssHeadlinesOnly;
	}

	public boolean isEditMode() {
		return editMode;
	}

	public void setEditMode(boolean editMode) {
		this.editMode = editMode;
	}
	
	public boolean isShowMetaData() {
		return showMetaData;
	}

	public void setShowMetaData(boolean showMetaData) {
		this.showMetaData = showMetaData;
	}

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

	public boolean isShowParents() {
		return showParents;
	}

	public void setShowParents(boolean showParents) {
		this.showParents = showParents;
	}
}
