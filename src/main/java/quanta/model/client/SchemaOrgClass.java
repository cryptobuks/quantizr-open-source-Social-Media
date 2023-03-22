package quanta.model.client;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;

@Data
@JsonInclude(Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaOrgClass {
    private String id;
    private String comment;
    private String label;
    private List<SchemaOrgProp> props = new ArrayList<>();

    public SchemaOrgClass() {}
}
