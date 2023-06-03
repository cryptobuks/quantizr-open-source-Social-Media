package quanta.actpub.model;

import java.util.Map;
import quanta.actpub.APConst;

/**
 * Follow object
 */
public class APOFollow extends APOActivity {
    public APOFollow(Map<?, ?> obj) {
        super(obj);
    }

    public APOFollow() {
        put(context, APConst.CONTEXT_STREAMS);

        // NOTE: Pleroma uses an array similar to this.
        // put(context, new APList().val(APConst.CONTEXT_STREAMS).val(new APObj().put("@language", "und")));

        put(type, APType.Follow);
    }

    /* 'actor' is person doing the following, and 'target' is the person being followed */
    public APOFollow(String id, String actor, String target) {
        this();
        if (id != null) {
            put(APObj.id, id);
        }
        put(APObj.actor, actor);

        // Pleroma hacking (nothing with pleroma has ever worked)
        // put("state", "pending");

        // NOTE: I saw on a Pleroma forum that they DO need these cc and to as LISTS
        // even though the AP SPEC allows for strings.
        put(APObj.cc, new APList());
        put(APObj.to, new APList().val(target));

        // This format is known to work on all platforms except Pleroma. Never have gotten Pleroma to work
        // Pleroma always sends back a 400 error.
        put(APObj.object, target);

        // NOTE: This Person object is the format I've seen some servers use, and we do support this format
        // for inbound follows.
        // put(APObj.object, new APOPerson(target));
    }

    @Override
    public APOFollow put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
