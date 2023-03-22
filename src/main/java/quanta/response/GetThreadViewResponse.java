package quanta.response;

import java.util.List;
import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetThreadViewResponse extends ResponseBase {
    private List<NodeInfo> nodes;
    private boolean topReached;
}
