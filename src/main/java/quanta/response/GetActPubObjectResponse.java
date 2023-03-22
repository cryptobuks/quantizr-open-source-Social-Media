package quanta.response;

import quanta.response.base.ResponseBase;
import quanta.model.NodeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

public class GetActPubObjectResponse extends ResponseBase {
    private NodeInfo node;
}
