package quanta.model.client;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

// "lud16" : "outsideice77@walletofsatoshi.com",
// "lud06" : "",

@Data
@NoArgsConstructor
public class NostrMetadata {
    private String name;
    private String username;

    @JsonProperty("display_name")
    private String displayName;

    private String about;
    private String picture; 
    private String banner;
    private String website;
    private String nip05;
}
