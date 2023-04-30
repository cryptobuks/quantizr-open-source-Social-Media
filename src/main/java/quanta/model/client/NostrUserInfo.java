package quanta.model.client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
/* WARNING: This object is serialized */
public class NostrUserInfo {
    private String pk;
    private String npub;
}
