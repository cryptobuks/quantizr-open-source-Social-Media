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

    // "tags": [
    //     ["e", <32-bytes hex of the id of another event>, <recommended relay URL>],
    //     ["p", <32-bytes hex of a pubkey>, <recommended relay URL>],
    //     ... // other kinds of tags may be included later
    // ],
    private ArrayList<ArrayList<String>> tags;
    private Long timestamp;

    // Non-Nostr Properties used by Quanta only
    private String npub; // if this is a user metadata event we put the npub here.
    private String relays; // if user metadate client will send relays here
}
