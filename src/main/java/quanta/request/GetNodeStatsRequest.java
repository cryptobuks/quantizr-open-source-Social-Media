package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetNodeStatsRequest extends RequestBase {
    private String nodeId;
    private boolean trending;
    private boolean signatureVerify;

    /* True if this will be the trending button on the Feed tab running this for the Feed tab */
    private boolean feed;

    private boolean getWords;
    private boolean getMentions;
    private boolean getTags;
}
