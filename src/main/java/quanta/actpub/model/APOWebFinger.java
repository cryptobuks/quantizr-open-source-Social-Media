package quanta.actpub.model;

import quanta.actpub.APConst;

/**
 * WebFingerObject object.
 */
public class APOWebFinger extends APObj {

    public APOWebFinger(String fullName, String url) {
        super();
        put(APObj.subject, "acct:" + fullName);
        put(
            APObj.links,
            new APList() //
                .val(
                    new APObj() //
                        .put(APObj.rel, "self") //
                        .put(APObj.type, APConst.CTYPE_ACT_JSON) //
                        .put(APObj.href, url)
                )
        );
    }

    @Override
    public APOWebFinger put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
