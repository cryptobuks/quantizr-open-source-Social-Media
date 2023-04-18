package quanta.model.client;

import java.util.ArrayList;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
/* WARNING: This object is serialized */
public class NostrEvent {
    private String id;
    private String sig;
    private String pk;
    private Integer kind;
    private String content;
    private ArrayList<ArrayList<String>> tags;
    private Long timestamp;

    // Non-Nostr Properties used by Quanta only
    private String npub; // if this is a user metadata event we put the npub here.
}
