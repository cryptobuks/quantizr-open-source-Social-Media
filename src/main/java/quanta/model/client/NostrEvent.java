package quanta.model.client;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NostrEvent {
    private String id;
    private String sig;
    private String pk;
    private Integer kind;
    private String content;
    private Long timestamp;
}
