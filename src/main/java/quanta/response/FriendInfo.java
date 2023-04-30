package quanta.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FriendInfo {
    private String displayName;
    private String userName;
    private String relays; // note: Relays is not STORED in the actual friend node but only on the account node.
    private String avatarVer;
    private String userNodeId;
    private String friendNodeId;
    private String foreignAvatarUrl;
    private String tags;

    // indicates this user liked some node, and is dependent upon use case where this FriendInfo is
    // being used
    private Boolean liked;
}
