package quanta.model.ipfs.dag;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class DagNode {

    // don't need this, make it an Object for now.
    @JsonProperty("Data")
    private Object data;

    @JsonProperty("Links")
    private List<DagLink> links;

    public Object getData() {
        return this.data;
    }

    public List<DagLink> getLinks() {
        return this.links;
    }

    @JsonProperty("Data")
    public void setData(final Object data) {
        this.data = data;
    }

    @JsonProperty("Links")
    public void setLinks(final List<DagLink> links) {
        this.links = links;
    }

    public DagNode() {}
}
