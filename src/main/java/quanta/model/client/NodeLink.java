package quanta.model.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeLink {

    private Integer ordinal = 0;
    private String nodeId;
    private String name;

    public NodeLink() {}

    @JsonProperty("o")
    public Integer getOrdinal() {
        return ordinal;
    }

    @JsonProperty("o")
    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }

    @JsonProperty("i")
    public String getNodeId() {
        return nodeId;
    }

    @JsonProperty("i")
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    @JsonProperty("n")
    public String getName() {
        return name;
    }

    @JsonProperty("n")
    public void setName(String name) {
        this.name = name;
    }
}
