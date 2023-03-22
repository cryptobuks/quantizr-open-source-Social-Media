package quanta.model.client;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IPSMMessage {
    private String from;
    private String sig;
    private List<IPSMData> content;
    private long ts;
}
