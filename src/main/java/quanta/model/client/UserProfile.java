package quanta.model.client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserProfile {
	private String displayName;

	private String userName;
	// if a node exists named '[userName]:home', then the id of that node is stored here.
	private String homeNodeId;

	private String didIPNS;
	private boolean mfsEnable;

	private String userBio;
	private String userTags;
	private String blockedWords;
	private String recentTypes;

	// version (which is now just the GRID ID) needed to retrieve profile image (account node binary
	// attachment)
	// Moving out of here into getUserProfile
	private String avatarVer;

	private String headerImageVer;

	private String userNodeId;

	/* for foreign users this will point to their user avatar image */
	private String apIconUrl;

	/* for foreign users this will point to their user image (i.e. header image) */
	private String apImageUrl;

	/* for foreign users this will be their actor url */
	private String actorUrl;
	private String actorId;

	private int followerCount;
	private int followingCount;

	/*
	 * Indicators to the person querying this info about whether they follow or blocked this user
	 */
	private boolean following;
	private boolean blocked;

	private String relays;
	private String nostrNpub;
	private Long nostrTimestamp;
}
