package quanta.model.client;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NostrQuery {
    private List<String> authors;
    private List<Integer> kinds;
    private Integer limit;
    private Long since;
}
