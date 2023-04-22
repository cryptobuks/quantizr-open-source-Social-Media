package quanta.response;

import java.util.List;
import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetThreadViewResponse extends ResponseBase {
    private List<NodeInfo> nodes;
    private boolean topReached;

    // If user attempted to get a ThreadView for a node that's a Nostr node and the server
    // (i.e. database) didn't have enough information we can optionally send back as much of the thread
    // history as we WERE able to get from the server, and then send back nostrDeadEnd==true so the client
    // can start traversing up the tree from there.
    private boolean nostrDeadEnd;
}
