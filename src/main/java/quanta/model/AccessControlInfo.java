package quanta.model;

import java.util.LinkedList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a certain principal and a set of privileges the principal has.
 */
@Data
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class AccessControlInfo {
	private String displayName;
	private String principalName;
	private String principalNodeId;
	private String nostrNpub;
	private String nostrRelays;

	// used to build local user avatars
	private String avatarVer;

	// used to hold foreign user avatars (not always populated)
	private String foreignAvatarUrl;

	private List<PrivilegeInfo> privileges;
	private String publicKey;

	public AccessControlInfo(String displayName, String principalName, String principalNodeId, String publicKey, String nostrNpub, String nostrRelays, String avatarVer,
			String foreignAvatarUrl) {
		this.displayName = displayName;
		this.principalName = principalName;
		this.principalNodeId = principalNodeId;
		this.publicKey = publicKey;
		this.avatarVer = avatarVer;
		this.foreignAvatarUrl = foreignAvatarUrl;
		this.nostrNpub = nostrNpub;
		this.nostrRelays = nostrRelays;
	}

	public void addPrivilege(PrivilegeInfo priv) {
		if (privileges == null) {
			privileges = new LinkedList<>();
		}
		privileges.add(priv);
	}
}
