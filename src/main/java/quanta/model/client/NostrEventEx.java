package quanta.model.client;

import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
/*
 * This is the exact NostrEvent format we will get from servers
 * 
 * todo-000: We need to convert the NostrEvent over to this object and not consolidate into one.
 */
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
