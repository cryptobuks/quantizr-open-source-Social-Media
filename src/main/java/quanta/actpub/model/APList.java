package quanta.actpub.model;

import java.util.LinkedList;
import java.util.List;

/**
 * List of objects
 */
public class APList extends LinkedList<Object> {
    public APList() {
    }

    public APList(List<?> val) {
        super.addAll(val);
    }

    public APList val(Object val) {
        super.add(val);
        return this;
    }

    public APList vals(List<?> val) {
        super.addAll(val);
        return this;
    }
}
