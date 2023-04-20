package quanta.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import quanta.request.base.RequestBase;

@Data
@NoArgsConstructor
public class SaveNostrSettingsRequest extends RequestBase {
    // whose identity we're updating (can be currently logged in user (sent as null) or a Friend (sent as Friend Node Id)
    public String target;

    public String key;

    // newline delimited list of relays
    public String relays;
}
