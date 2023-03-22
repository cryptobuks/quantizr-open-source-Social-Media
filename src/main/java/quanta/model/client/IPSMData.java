package quanta.model.client;

import lombok.Data;

@Data
// warning: used in signature validation. Don't alter property order.
public class IPSMData {
    private String mime;
    private String data;
}
