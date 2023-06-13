package quanta.actpub.model;

import java.util.Map;
import quanta.actpub.APConst;

/**
 * Undo object
 */
public class APOUndo extends APOActivity {

    public APOUndo(Map<?, ?> obj) {
        super(obj);
    }

    public APOUndo() {
        put(context, APConst.CONTEXT_STREAMS);
        put(type, APType.Undo);
    }

    public APOUndo(String id, String actor, Object object) {
        this();
        if (id != null) {
            put(APObj.id, id);
        }
        put(APObj.actor, actor);
        put(APObj.object, object);
    }

    @Override
    public APOUndo put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
