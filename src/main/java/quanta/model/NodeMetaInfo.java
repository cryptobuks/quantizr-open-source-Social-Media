package quanta.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NodeMetaInfo {
    private String title;
    private String description;
    private String attachmentMime;
    private String attachmentUrl;
    private String url;
}
