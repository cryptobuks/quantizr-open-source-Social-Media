package quanta.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IPInfo {
    private Object lock = new Object();
    private long lastRequestTime;
}
