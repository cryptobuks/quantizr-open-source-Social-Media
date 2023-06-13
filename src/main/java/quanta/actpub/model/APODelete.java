package quanta.actpub.model;

import java.util.Map;
import quanta.actpub.APConst;

/**
 * Delete object.
 */
public class APODelete extends APOActivity {

    public APODelete(Map<?, ?> obj) {
        super(obj);
    }

    public APODelete() {
        put(
            context,
            new APList() //
                .val(APConst.CONTEXT_STREAMS) //
                .val(new APOLanguage())
        );
        put(type, APType.Delete);
    }

    public APODelete(String id, String actor, APObj object, APList to) {
        this();
        put(APObj.id, id);
        put(APObj.actor, actor);
        put(APObj.object, object);
        if (to != null) {
            put(APObj.to, to);
        }
    }

    @Override
    public APODelete put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
