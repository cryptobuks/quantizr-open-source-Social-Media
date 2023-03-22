package quanta.model.client;

import java.util.List;

import lombok.Data;

@Data
public class RssFeed {
    private String encoding;
    private String title;
    private String description;
    private String author;
    private String link;
    private String image;
    private List<RssFeedEntry> entries;
}
