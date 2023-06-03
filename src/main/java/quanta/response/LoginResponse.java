
package quanta.response;

import quanta.model.UserPreferences;
import quanta.model.client.UserProfile;
import quanta.response.base.ResponseBase;

public class LoginResponse extends ResponseBase {
	// now that we have userProfile in this object (a new change) some of the other properties
	// should be redundant and can be removed
	private UserProfile userProfile;
	private String authToken;
	private String rootNodePath;
	private String allowedFeatures;
	private String anonUserLandingPageNode;
	private UserPreferences userPreferences;
	private boolean allowFileSystemSearch;

	public UserProfile getUserProfile() {
		return this.userProfile;
	}
	
	public String getAuthToken() {
		return this.authToken;
	}
	
	public String getRootNodePath() {
		return this.rootNodePath;
	}
	
	public String getAllowedFeatures() {
		return this.allowedFeatures;
	}
	
	public String getAnonUserLandingPageNode() {
		return this.anonUserLandingPageNode;
	}
	
	public UserPreferences getUserPreferences() {
		return this.userPreferences;
	}
	
	public boolean isAllowFileSystemSearch() {
		return this.allowFileSystemSearch;
	}
	
	public void setUserProfile(final UserProfile userProfile) {
		this.userProfile = userProfile;
	}
	
	public void setAuthToken(final String authToken) {
		this.authToken = authToken;
	}
	
	public void setRootNodePath(final String rootNodePath) {
		this.rootNodePath = rootNodePath;
	}
	
	public void setAllowedFeatures(final String allowedFeatures) {
		this.allowedFeatures = allowedFeatures;
	}
	
	public void setAnonUserLandingPageNode(final String anonUserLandingPageNode) {
		this.anonUserLandingPageNode = anonUserLandingPageNode;
	}
	
	public void setUserPreferences(final UserPreferences userPreferences) {
		this.userPreferences = userPreferences;
	}
	
	public void setAllowFileSystemSearch(final boolean allowFileSystemSearch) {
		this.allowFileSystemSearch = allowFileSystemSearch;
	}
	
	public LoginResponse() {
	}
}
