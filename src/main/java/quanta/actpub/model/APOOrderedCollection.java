package quanta.actpub.model;

import java.util.Collection;
import quanta.actpub.APConst;

public class APOOrderedCollection extends APObj {

    public APOOrderedCollection() {
        put(context, APConst.CONTEXT_STREAMS);
        put(type, APType.OrderedCollection);
    }

    /* Constructor for wrapping into first/last URLs */
    public APOOrderedCollection(String id, Long totalItems, String first, String last) {
        this();
        put(APObj.id, id);
        put(APObj.totalItems, totalItems);
        put(APObj.first, first);
        put(APObj.last, last);
    }

    /* Constructor for wrapping actual embedded item content */
    public APOOrderedCollection(String id, Collection items) {
        this();
        put(APObj.id, id);
        put(APObj.totalItems, items.size());
        put(APObj.orderedItems, items);
    }

    @Override
    public APOOrderedCollection put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
