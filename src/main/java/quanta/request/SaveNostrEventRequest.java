package quanta.request;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import quanta.model.client.NostrEventWrapper;
import quanta.model.client.NostrUserInfo;
import quanta.request.base.RequestBase;

@Data
@NoArgsConstructor
public class SaveNostrEventRequest extends RequestBase {
    public List<NostrEventWrapper> events;
    public List<NostrUserInfo> userInfo;
}
