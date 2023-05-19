package quanta.model.client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
/* WARNING: This object is serialized */
public class NostrUserInfo {
    private String pk;
    private String npub;

    // only used when this object is part of a server side push to send down to client to populate and
    // save user
    private String relays;

    public NostrUserInfo(String pk, String npub, String relays) {
        this.pk = pk;
        this.npub = npub;
        this.relays = relays;
    }
}
