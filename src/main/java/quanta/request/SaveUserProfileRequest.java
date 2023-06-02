
package quanta.request;

import quanta.request.base.RequestBase;

public class SaveUserProfileRequest extends RequestBase {
	private String userName;
	private String userBio;
	private String userTags;
	private String blockedWords;
	private String recentTypes;
	private String displayName;
	// only publishes DID/IPNS if this is true
	private boolean publish;
	private boolean mfsEnable;

	
	public String getUserName() {
		return this.userName;
	}

	
	public String getUserBio() {
		return this.userBio;
	}

	
	public String getUserTags() {
		return this.userTags;
	}

	
	public String getBlockedWords() {
		return this.blockedWords;
	}

	
	public String getRecentTypes() {
		return this.recentTypes;
	}

	
	public String getDisplayName() {
		return this.displayName;
	}

	
	public boolean isPublish() {
		return this.publish;
	}

	
	public boolean isMfsEnable() {
		return this.mfsEnable;
	}

	
	public void setUserName(final String userName) {
		this.userName = userName;
	}

	
	public void setUserBio(final String userBio) {
		this.userBio = userBio;
	}

	
	public void setUserTags(final String userTags) {
		this.userTags = userTags;
	}

	
	public void setBlockedWords(final String blockedWords) {
		this.blockedWords = blockedWords;
	}

	
	public void setRecentTypes(final String recentTypes) {
		this.recentTypes = recentTypes;
	}

	
	public void setDisplayName(final String displayName) {
		this.displayName = displayName;
	}

	
	public void setPublish(final boolean publish) {
		this.publish = publish;
	}

	
	public void setMfsEnable(final boolean mfsEnable) {
		this.mfsEnable = mfsEnable;
	}

	public SaveUserProfileRequest() {
	}
}
