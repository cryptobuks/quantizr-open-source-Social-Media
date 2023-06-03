
package quanta.response;

import quanta.model.client.OpenGraph;
import quanta.response.base.ResponseBase;

public class GetOpenGraphResponse extends ResponseBase {
    private OpenGraph openGraph;

    public OpenGraph getOpenGraph() {
        return this.openGraph;
    }
    
    public void setOpenGraph(final OpenGraph openGraph) {
        this.openGraph = openGraph;
    }

    public GetOpenGraphResponse() {
    }
}
