package quanta.actpub.model;

import java.util.Map;
import quanta.actpub.APConst;

/**
 * Person object
 */
public class APOPerson extends APOActor {

    public APOPerson(Map<?, ?> obj) {
        super(obj);
    }

    public APOPerson() {
        put(
            APObj.context,
            new APList() //
                .val(APConst.CONTEXT_STREAMS) //
                .val(APConst.CONTEXT_SECURITY)
        );
        put(APObj.type, APType.Person); //
    }

    @Override
    public APOPerson put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
