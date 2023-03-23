package quanta.actpub.model;

import static quanta.actpub.model.AP.apStr;
import java.util.HashMap;
import java.util.Map;

/**
 * The objects in the ActivityPub Spec are so highly dynamic that we cannot determine ahead of time
 * what the property typoes of any reply will be so we just use a map, which can successfully
 * unmarshall any format of JSON thrown at us, and then we can let out getter methods be smart
 * enough to extract what we need out of these objects.
 * 
 * todo-2: Consider a refactor to use: org.json.JSONObject, and org.json.JSONArray, instead of this
 * APObj
 */
public class APObj extends HashMap<String, Object> {
    public static final String id = "id";
    public static final String context = "@context";
    public static final String type = "type";
    public static final String did = "did";
    public static final String language = "@language"; // NOTE: I had this as "language" for a long time, which I guess was
                                                       // getting ignored
    public static final String object = "object";
    public static final String actor = "actor";
    public static final String published = "published";
    public static final String url = "url";
    public static final String inReplyTo = "inReplyTo";
    public static final String summary = "summary";
    public static final String followers = "followers";
    public static final String following = "following";
    public static final String preferredUsername = "preferredUsername";
    public static final String name = "name";
    public static final String mediaType = "mediaType";
    public static final String icon = "icon";
    public static final String image = "image";
    public static final String inbox = "inbox";
    public static final String sharedInbox = "sharedInbox";
    public static final String outbox = "outbox";
    public static final String totalItems = "totalItems";
    public static final String first = "first";
    public static final String next = "next";
    public static final String last = "last";
    public static final String href = "href";
    public static final String subject = "subject";
    public static final String links = "links";
    public static final String to = "to";
    public static final String cc = "cc";
    public static final String attachment = "attachment";
    public static final String attributedTo = "attributedTo";
    public static final String sensitive = "sensitive";
    public static final String content = "content";
    public static final String tag = "tag";
    public static final String replies = "replies";
    public static final String orderedItems = "orderedItems";
    public static final String items = "items";
    public static final String partOf = "partOf";
    public static final String rel = "rel";
    public static final String endpoints = "endpoints";
    public static final String publicKey = "publicKey";
    public static final String publicKeyPem = "publicKeyPem";
    public static final String owner = "owner";
    public static final String supportsFriendRequests = "supportsFriendRequests";

    public APObj() {}

    public APObj(Map<?, ?> obj) {
        if (obj != null) {
            this.putAll((Map<String, Object>) obj);
        }
    }

    public String getType() {
        String type = apStr(this, APObj.type);
        return type != null ? type.trim() : null;
    }

    // todo-1: potentially this should ONLY be in APOActivity
    public String getActor() {
        return apStr(this, APObj.actor);
    }

    public String getId() {
        return apStr(this, APObj.id);
    }

    public APObj put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
