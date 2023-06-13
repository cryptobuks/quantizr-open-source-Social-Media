package quanta.model;

public class NodeMetaInfo {

    private String title;
    private String description;
    private String attachmentMime;
    private String attachmentUrl;
    private String url;

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public String getAttachmentMime() {
        return this.attachmentMime;
    }

    public String getAttachmentUrl() {
        return this.attachmentUrl;
    }

    public String getUrl() {
        return this.url;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public void setAttachmentMime(final String attachmentMime) {
        this.attachmentMime = attachmentMime;
    }

    public void setAttachmentUrl(final String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public NodeMetaInfo() {}
}
