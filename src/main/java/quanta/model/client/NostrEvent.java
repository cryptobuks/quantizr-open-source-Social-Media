package quanta.model.client;

import java.util.ArrayList;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NostrEvent {
    private String id;
    private String sig;
    private String pubkey;
    private Integer kind;
    private String content;
    private ArrayList<ArrayList<String>> tags;
    private Long createdAt;
}
