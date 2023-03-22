package quanta.model.client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OpenGraph {
    private String url;
    private String title;
    private String description;
    private String image;
}
