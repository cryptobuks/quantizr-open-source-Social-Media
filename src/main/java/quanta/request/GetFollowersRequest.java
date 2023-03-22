package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetFollowersRequest extends RequestBase {
    private int page;

    /*
     * user to get followers of (if this is a foreign user, of course it needs to go thru ActivityPub)
     */
    private String targetUserName;
}
