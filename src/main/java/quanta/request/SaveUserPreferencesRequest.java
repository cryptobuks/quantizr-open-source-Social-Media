
package quanta.request;

import quanta.model.UserPreferences;
import quanta.request.base.RequestBase;

public class SaveUserPreferencesRequest extends RequestBase {
	private String userNodeId;
	private UserPreferences userPreferences;

	public String getUserNodeId() {
		return this.userNodeId;
	}
	
	public UserPreferences getUserPreferences() {
		return this.userPreferences;
	}
	
	public void setUserNodeId(final String userNodeId) {
		this.userNodeId = userNodeId;
	}
	
	public void setUserPreferences(final UserPreferences userPreferences) {
		this.userPreferences = userPreferences;
	}

	public SaveUserPreferencesRequest() {
	}
}
