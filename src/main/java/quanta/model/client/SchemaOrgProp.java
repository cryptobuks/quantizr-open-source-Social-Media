package quanta.model.client;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaOrgProp {
    private String comment;
    private String label;
    private List<SchemaOrgRange> ranges = new ArrayList<>();

    public SchemaOrgProp() {}

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<SchemaOrgRange> getRanges() {
        return ranges;
    }

    public void setRanges(List<SchemaOrgRange> ranges) {
        this.ranges = ranges;
    }
}
