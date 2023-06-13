package quanta.actpub.model;

// "type" : "Hashtag",
// "href" : "https://...",
// "name" : "#kittens"

public class APOHashtag extends APObj {

    APOHashtag() {
        put(type, APType.Hashtag);
    }

    public APOHashtag(String href, String name) {
        this();
        put(APObj.href, href);
        put(APObj.name, name);
    }

    @Override
    public APOHashtag put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
