package quanta.actpub.model;

import quanta.actpub.APConst;

public class APOOrderedCollectionPage extends APObj {

    public APOOrderedCollectionPage() {
        put(context, APConst.CONTEXT_STREAMS);
        put(type, APType.OrderedCollectionPage);
    }

    public APOOrderedCollectionPage(String id, Object orderedItems, String partOf, int totalItems) {
        this();
        put(APObj.id, id);
        put(APObj.orderedItems, orderedItems);
        put(APObj.partOf, partOf);
        put(APObj.totalItems, totalItems);
    }

    @Override
    public APOOrderedCollectionPage put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
