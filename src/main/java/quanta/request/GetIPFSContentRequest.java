package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetIPFSContentRequest extends RequestBase {
    // rename this to 'mfsPath'
    private String id;

}
