package quanta.actpub.model;

import java.util.Map;

public class APOUrl extends APObj {

    public APOUrl(Map<?, ?> obj) {
        super(obj);
    }

    public APOUrl() {}

    public APOUrl(String type, String mediaType, String href) {
        this();
        put(APObj.type, type);
        put(APObj.mediaType, mediaType);
        put(APObj.href, href);
    }

    @Override
    public APOUrl put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
