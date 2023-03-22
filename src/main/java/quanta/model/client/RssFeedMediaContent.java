package quanta.model.client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RssFeedMediaContent {
    private String type;
    private String url;
    private String medium;
}
