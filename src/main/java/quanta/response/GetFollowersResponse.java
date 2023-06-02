
package quanta.response;

import java.util.List;
import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;

public class GetFollowersResponse extends ResponseBase {
    /* orderablility of children not set in these objects, all will be false */
    private List<NodeInfo> searchResults;

    
    public List<NodeInfo> getSearchResults() {
        return this.searchResults;
    }

    
    public void setSearchResults(final List<NodeInfo> searchResults) {
        this.searchResults = searchResults;
    }

    public GetFollowersResponse() {
    }
}
