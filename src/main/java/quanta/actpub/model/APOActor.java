package quanta.actpub.model;
import java.util.Map;

public class APOActor extends APObj {
    public APOActor() {
        super();
    }

    public APOActor(Map<?, ?> obj) {
        super(obj);
    }

    @Override
    public APOActor put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
