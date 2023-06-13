package quanta.actpub.model;

import static quanta.actpub.model.AP.apAPObj;
import static quanta.actpub.model.AP.apObj;
import static quanta.actpub.model.AP.apStr;

import java.util.Map;

public class APOActivity extends APObj {

    public APOActivity() {}

    public APOActivity(Map<?, ?> obj) {
        super(obj);
    }

    public String getActor() {
        return apStr(this, APObj.actor);
    }

    public Object getObject() {
        return apObj(this, APObj.object);
    }

    /* Turns the correct typed concrete class from the 'object' property */
    public APObj getAPObj() {
        return AP.typeFromFactory(apAPObj(this, APObj.object));
    }

    public APOActivity put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
