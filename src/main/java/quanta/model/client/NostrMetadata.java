package quanta.model.client;
import lombok.Data;
import lombok.NoArgsConstructor;

// "website" : "",
// "nip05" : "scsibug@wellorder.net",
// "displayName" : "",
// "name" : "scsibug",
// "about" : "author of nostr-rs-relay, operates nostr-pub.wellorder.net",
// "lud16" : "outsideice77@walletofsatoshi.com",
// "lud06" : "",
// "banner" : "",
// "display_name" : "",
// "picture" : "https://s.gravatar.com/avatar/1a425d744df94198d68dfffbf7ae51cf",
// "username" : "scsibug"

@Data
@NoArgsConstructor
public class NostrMetadata {
    private String name;
    private String username;
    private String displayName;
    private String about;
    private String picture; 
}
