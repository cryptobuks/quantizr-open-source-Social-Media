package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
}
