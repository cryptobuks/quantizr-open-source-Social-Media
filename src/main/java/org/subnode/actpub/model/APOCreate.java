package org.subnode.actpub.model;

import org.subnode.actpub.APConst;
import static org.subnode.util.Util.*;

public class APOCreate extends APObj {
    public APOCreate() {
        put(context, new APList() //
                .val(APConst.CONTEXT_STREAMS) //
                .val(new APOLanguage()));
        put(type, APType.Create);
    }

    public APOCreate(String id, String actor, String published, APObj object, APList to) {
        this();
        put(APObj.id, id);
        put(APObj.actor, actor);
        put(APObj.published, published);
        put(APObj.object, object);
        if (ok(to)) {
            put(APObj.to, to);
        }
    }

    @Override
    public APOCreate put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
