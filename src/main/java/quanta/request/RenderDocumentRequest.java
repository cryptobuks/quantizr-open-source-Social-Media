package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RenderDocumentRequest extends RequestBase {
    private String rootId;
    private String startNodeId;
    private boolean includeComments;
}
