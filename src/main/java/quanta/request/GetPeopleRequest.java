package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetPeopleRequest extends RequestBase {

    // if nodeId is non-null we return only the info for the users associated with that node, whichi
    // means everyone mentioned in the text plus, everyone in the shares.
    private String nodeId;
    private String type; // friends | blocks
    private String subType; // null | nostr
}
