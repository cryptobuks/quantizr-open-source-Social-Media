package quanta.response;

import quanta.model.UserPreferences;
import quanta.model.client.UserProfile;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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

	// comes back to caller as TRUE if the keys were not the same as the keys stored on the server.
	private boolean unknownPubEncKey;
	private boolean unknownPubSigKey;
}
