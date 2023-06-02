
package quanta.model.client;

import java.util.List;

public class RssFeed {
    private String encoding;
    private String title;
    private String description;
    private String author;
    private String link;
    private String image;
    private List<RssFeedEntry> entries;

    
    public String getEncoding() {
        return this.encoding;
    }

    
    public String getTitle() {
        return this.title;
    }

    
    public String getDescription() {
        return this.description;
    }

    
    public String getAuthor() {
        return this.author;
    }

    
    public String getLink() {
        return this.link;
    }

    
    public String getImage() {
        return this.image;
    }

    
    public List<RssFeedEntry> getEntries() {
        return this.entries;
    }

    
    public void setEncoding(final String encoding) {
        this.encoding = encoding;
    }

    
    public void setTitle(final String title) {
        this.title = title;
    }

    
    public void setDescription(final String description) {
        this.description = description;
    }

    
    public void setAuthor(final String author) {
        this.author = author;
    }

    
    public void setLink(final String link) {
        this.link = link;
    }

    
    public void setImage(final String image) {
        this.image = image;
    }

    
    public void setEntries(final List<RssFeedEntry> entries) {
        this.entries = entries;
    }
    
    public RssFeed() {
    }
}
