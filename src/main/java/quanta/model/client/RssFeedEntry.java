package quanta.model.client;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RssFeedEntry {
    private String parentFeedTitle;
    private String author;
    private String title;
    private String subTitle;
    private String publishDate;
    private String image;
    private String thumbnail;
    private String description;
    private String link;
    private List<RssFeedEnclosure> enclosures;
    private List<RssFeedMediaContent> mediaContent;
}
