package quanta.actpub.model;

public class APOTombstone extends APObj {

    public APOTombstone() {
        // not adding this. Mastodon doesn't.
        // put(context, new APList() //
        // .val(APConst.CONTEXT_STREAMS) //
        // .val(new APOLanguage()));
        put(type, APType.Tombstone);
    }

    public APOTombstone(String id) {
        this();
        put(APObj.id, id);
    }

    @Override
    public APOTombstone put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
