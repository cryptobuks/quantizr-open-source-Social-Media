package quanta.model.client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NostrEventWrapper {
    private NostrEvent event;
    private String nodeId; // quanta nodeId if known, or else null
    private String npub; // if this is a user metadata event we put the npub here.
    private String relays; // if user metadate client will send relays here
}
