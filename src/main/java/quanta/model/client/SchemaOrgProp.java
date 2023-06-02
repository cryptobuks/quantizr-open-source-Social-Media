
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

    
    public String getComment() {
        return this.comment;
    }

    
    public String getLabel() {
        return this.label;
    }

    
    public List<SchemaOrgRange> getRanges() {
        return this.ranges;
    }

    
    public void setComment(final String comment) {
        this.comment = comment;
    }

    
    public void setLabel(final String label) {
        this.label = label;
    }

    
    public void setRanges(final List<SchemaOrgRange> ranges) {
        this.ranges = ranges;
    }
    
    public SchemaOrgProp() {
    }
}
