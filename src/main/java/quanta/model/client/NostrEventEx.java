package quanta.model.client;

import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NostrEventEx {
    private String id;
    private String sig;
    private String pubkey;
    private Integer kind;
    private String content;
    private ArrayList<ArrayList<String>> tags;

    @JsonProperty("created_at")
    private Long createdAt;
}
