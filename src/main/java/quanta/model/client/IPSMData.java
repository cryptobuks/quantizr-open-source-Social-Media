package quanta.model.client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
// warning: used in signature validation. Don't alter property order.
public class IPSMData {
    private String mime;
    private String data;
}
