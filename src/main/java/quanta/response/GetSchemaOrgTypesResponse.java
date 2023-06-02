
package quanta.response;

import java.util.List;
import quanta.model.client.SchemaOrgClass;
import quanta.response.base.ResponseBase;

public class GetSchemaOrgTypesResponse extends ResponseBase {
    public List<SchemaOrgClass> classes;

    
    public List<SchemaOrgClass> getClasses() {
        return this.classes;
    }

    
    public void setClasses(final List<SchemaOrgClass> classes) {
        this.classes = classes;
    }

    public GetSchemaOrgTypesResponse() {
    }
}
