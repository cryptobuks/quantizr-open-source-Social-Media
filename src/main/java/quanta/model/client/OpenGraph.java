package quanta.model.client;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OpenGraph {
    // when we check a url and it's not able to provide OpenGraph data we at least send back
    // the mime type in that case, so that the browser can perhaps render images etc.
    private String mime;

    private String url;
    private String title;
    private String description;
    private String image;
}
