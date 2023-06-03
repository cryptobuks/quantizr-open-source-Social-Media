package quanta.actpub.model;

import org.apache.commons.lang3.StringUtils;
import quanta.actpub.APConst;

/**
 * Note object
 */
public class APONote extends APObj {

    // Constructor to use when injesting from a foreign source, an objec that's KNOWN to be a "note"
    public APONote(APObj obj) {
        this.putAll(obj);
    }

    public APONote() {
        put(context, new APList() //
                .val(APConst.CONTEXT_STREAMS) //
                .val(new APOLanguage()));
        put(type, APType.Note);
    }

    public APONote(String id, String published, String attributedTo, String summary, String url, String repliesUrl,
            boolean sensitive, String content, APList to) {
        this();
        put(APObj.id, id);
        put(APObj.published, published);
        put(APObj.attributedTo, attributedTo);

        if (!StringUtils.isEmpty(summary)) {
            put(APObj.summary, summary);
        }
        put(APObj.url, url);
        put(APObj.replies, repliesUrl);
        put(APObj.sensitive, sensitive);
        put(APObj.content, content);
        if (to != null) {
            put(APObj.to, to);
        }
    }

    @Override
    public APONote put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
