package quanta.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
}
