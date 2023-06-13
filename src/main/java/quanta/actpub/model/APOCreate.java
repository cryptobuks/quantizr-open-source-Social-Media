package quanta.actpub.model;

import java.util.Map;
import quanta.actpub.APConst;

/**
 * Create object.
 */
public class APOCreate extends APOActivity {

    public APOCreate(Map<?, ?> obj) {
        super(obj);
    }

    public APOCreate() {
        super();
        put(
            context,
            new APList() //
                .val(APConst.CONTEXT_STREAMS) //
                .val(new APOLanguage())
        );
        put(type, APType.Create);
    }

    public APOCreate(String id, String actor, String published, APObj object, APList to) {
        this();
        put(APObj.id, id);
        put(APObj.actor, actor);
        put(APObj.published, published);
        put(APObj.object, object);
        if (to != null) {
            put(APObj.to, to);
        }
    }

    @Override
    public APOCreate put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
