package quanta.actpub.model;

import java.util.Map;
import quanta.actpub.APConst;

/**
 * Accept object
 */
public class APOAccept extends APOActivity {
    public APOAccept(Map<?, ?> obj) {
        super(obj);
    }

    public APOAccept() {
        put(context, APConst.CONTEXT_STREAMS);
        put(type, APType.Accept);
    }

    public APOAccept(String actor, String toActor, String id, APObj object) {
        this();

        // trying to be compatable with other platforms which seem to have an empty cc array, rather than
        // omitting it.
        put(APObj.cc, new APList());

        put(APObj.actor, actor);
        put(APObj.to, new APList().val(toActor));
        put(APObj.id, id);
        put(APObj.object, object);
    }

    @Override
    public APOAccept put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
