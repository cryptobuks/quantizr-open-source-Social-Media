package quanta.response;

import java.util.List;
import quanta.model.client.SchemaOrgClass;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetSchemaOrgTypesResponse extends ResponseBase {
    public List<SchemaOrgClass> classes;
}
