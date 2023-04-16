package quanta.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import quanta.response.base.ResponseBase;

@Data
@NoArgsConstructor
public class SaveNostrEventResponse extends ResponseBase {

    // any accounts related to the request will be sent back as the MongoDB of the node created node
    private List<String> accntNodeIds;
}
