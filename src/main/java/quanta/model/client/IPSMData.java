package quanta.model.client;

// warning: used in signature validation. Don't alter property order.
public class IPSMData {

    private String mime;
    private String data;

    public String getMime() {
        return this.mime;
    }

    public String getData() {
        return this.data;
    }

    public void setMime(final String mime) {
        this.mime = mime;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public IPSMData() {}
}
