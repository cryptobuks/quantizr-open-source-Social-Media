package quanta.model.client;

/* WARNING: This object is serialized */
public class NostrUserInfo {

    private String pk;
    private String npub;
    // only used when this object is part of a server side push to send down to client to populate and
    // save user
    private String relays;

    public NostrUserInfo(String pk, String npub, String relays) {
        this.pk = pk;
        this.npub = npub;
        this.relays = relays;
    }

    public String getPk() {
        return this.pk;
    }

    public String getNpub() {
        return this.npub;
    }

    public String getRelays() {
        return this.relays;
    }

    public void setPk(final String pk) {
        this.pk = pk;
    }

    public void setNpub(final String npub) {
        this.npub = npub;
    }

    public void setRelays(final String relays) {
        this.relays = relays;
    }

    public NostrUserInfo() {}
}
