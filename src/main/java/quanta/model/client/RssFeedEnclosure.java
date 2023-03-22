package quanta.model.client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RssFeedEnclosure {
    private String type;
    private String url;
}
