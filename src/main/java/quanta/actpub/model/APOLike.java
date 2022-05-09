package quanta.actpub.model;

import static quanta.util.Util.ok;
import java.util.List;
import quanta.actpub.APConst;

/**
 * todo-1: we have @context but not "context" in this (might need research), I think 
 * @context is some kind of identification of "conversations"
 * 
 * Like object
 */
public class APOLike extends APObj {
    public APOLike() {
        put(context, new APList() //
                .val(APConst.CONTEXT_STREAMS) //
                .val(new APOLanguage()));
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

        if (ok(to)) {
            put(APObj.to, to);
        }
        if (ok(cc)) {
            put(APObj.cc, cc);
        }
    }

    @Override
    public APOLike put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
