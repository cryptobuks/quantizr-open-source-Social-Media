package quanta.model.client;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RssFeed {
    private String encoding;
    private String title;
    private String description;
    private String author;
    private String link;
    private String image;
    private List<RssFeedEntry> entries;
}
