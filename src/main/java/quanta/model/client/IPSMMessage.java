package quanta.model.client;

import java.util.List;
import lombok.Data;

@Data
public class IPSMMessage {
    private String from;
    private String sig;
    private List<IPSMData> content;
    private long ts;
}
