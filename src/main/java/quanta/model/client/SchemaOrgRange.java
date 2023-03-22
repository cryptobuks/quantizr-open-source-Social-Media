package quanta.model.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;

@Data
@JsonInclude(Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaOrgRange {
    private String id;

    public SchemaOrgRange() {}

    public SchemaOrgRange(String id) {
        super();
        this.id = id;
    }
}
