package quanta.actpub.model;

import java.util.Map;

public class APOIcon extends APObj {
    public APOIcon(Map<?, ?> obj) {
        super(obj);
    }

    public APOIcon() {}

    public APOIcon(String type, String mediaType, String url) {
        this();
        put(APObj.type, type);
        put(APObj.mediaType, mediaType);
        put(APObj.url, url);
    }

    @Override
    public APOIcon put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
