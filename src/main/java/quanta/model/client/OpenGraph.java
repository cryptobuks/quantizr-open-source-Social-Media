package quanta.model.client;

public class OpenGraph {

    // when we check a url and it's not able to provide OpenGraph data we at least send back
    // the mime type in that case, so that the browser can perhaps render images etc.
    private String mime;
    private String url;
    private String title;
    private String description;
    private String image;

    public String getMime() {
        return this.mime;
    }

    public String getUrl() {
        return this.url;
    }

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public String getImage() {
        return this.image;
    }

    public void setMime(final String mime) {
        this.mime = mime;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public void setImage(final String image) {
        this.image = image;
    }

    public OpenGraph() {}
}
