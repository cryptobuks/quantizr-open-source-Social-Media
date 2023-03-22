package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LuceneSearchRequest extends RequestBase {
    private String nodeId;
    private String text;
}

