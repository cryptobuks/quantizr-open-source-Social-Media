
package quanta.model.client;

import java.util.List;

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
    
    public String getParentFeedTitle() {
        return this.parentFeedTitle;
    }

    public String getAuthor() {
        return this.author;
    }
    
    public String getTitle() {
        return this.title;
    }
    
    public String getSubTitle() {
        return this.subTitle;
    }
    
    public String getPublishDate() {
        return this.publishDate;
    }

    public String getImage() {
        return this.image;
    }
    
    public String getThumbnail() {
        return this.thumbnail;
    }
    
    public String getDescription() {
        return this.description;
    }

    public String getLink() {
        return this.link;
    }
    
    public List<RssFeedEnclosure> getEnclosures() {
        return this.enclosures;
    }
    
    public List<RssFeedMediaContent> getMediaContent() {
        return this.mediaContent;
    }
    
    public void setParentFeedTitle(final String parentFeedTitle) {
        this.parentFeedTitle = parentFeedTitle;
    }
    
    public void setAuthor(final String author) {
        this.author = author;
    }
    
    public void setTitle(final String title) {
        this.title = title;
    }
    
    public void setSubTitle(final String subTitle) {
        this.subTitle = subTitle;
    }
    
    public void setPublishDate(final String publishDate) {
        this.publishDate = publishDate;
    }
    
    public void setImage(final String image) {
        this.image = image;
    }
    
    public void setThumbnail(final String thumbnail) {
        this.thumbnail = thumbnail;
    }
    
    public void setDescription(final String description) {
        this.description = description;
    }
    
    public void setLink(final String link) {
        this.link = link;
    }
    
    public void setEnclosures(final List<RssFeedEnclosure> enclosures) {
        this.enclosures = enclosures;
    }
    
    public void setMediaContent(final List<RssFeedMediaContent> mediaContent) {
        this.mediaContent = mediaContent;
    }
    
    public RssFeedEntry() {
    }
}
