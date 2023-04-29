package quanta.request;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import quanta.model.client.NostrEvent;
import quanta.request.base.RequestBase;

@Data
@NoArgsConstructor
public class SaveNostrEventRequest extends RequestBase {
    public List<NostrEvent> events;
}
