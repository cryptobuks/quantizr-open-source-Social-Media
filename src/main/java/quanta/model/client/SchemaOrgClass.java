
package quanta.model.client;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaOrgClass {
    private String id;
    private String comment;
    private String label;
    private List<SchemaOrgProp> props = new ArrayList<>();

    
    public String getId() {
        return this.id;
    }

    
    public String getComment() {
        return this.comment;
    }

    
    public String getLabel() {
        return this.label;
    }

    
    public List<SchemaOrgProp> getProps() {
        return this.props;
    }

    
    public void setId(final String id) {
        this.id = id;
    }

    
    public void setComment(final String comment) {
        this.comment = comment;
    }

    
    public void setLabel(final String label) {
        this.label = label;
    }

    
    public void setProps(final List<SchemaOrgProp> props) {
        this.props = props;
    }
    
    public SchemaOrgClass() {
    }
}
