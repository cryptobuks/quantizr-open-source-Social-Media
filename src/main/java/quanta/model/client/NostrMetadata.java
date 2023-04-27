package quanta.model.client;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private boolean reactions;
}
