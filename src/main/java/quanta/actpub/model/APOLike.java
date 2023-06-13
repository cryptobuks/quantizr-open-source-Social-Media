package quanta.actpub.model;

import java.util.List;
import java.util.Map;
import quanta.actpub.APConst;

/**
 * Like object
 */
public class APOLike extends APOActivity {

    public APOLike(Map<?, ?> obj) {
        super(obj);
    }

    public APOLike() {
        put(
            context,
            new APList() //
                .val(APConst.CONTEXT_STREAMS) //
                .val(new APOLanguage())
        );
        put(type, APType.Like);
    }

    /*
     * actor: ActorID (url) of person doing the like
     *
     * id = unique ID of this like object. (I'm going to try to use a fake on of these, now, we don't
     * support "likes" collections)
     *
     * objectId: id of thing being liked
     */
    public APOLike(String id, String objectId, String actor, List<String> to, List<String> cc) {
        this();
        put(APObj.id, id);
        put(APObj.actor, actor);
        put(APObj.object, objectId);

        if (to != null) {
            put(APObj.to, to);
        }
        if (cc != null) {
            put(APObj.cc, cc);
        }
    }

    @Override
    public APOLike put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
