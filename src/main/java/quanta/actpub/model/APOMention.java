package quanta.actpub.model;

/**
 * <pre>
{
    "@context": "https://www.w3.org/ns/activitystreams",
    "name": "A thank-you note",
    "type": "Note",
    "content": "Thank you @sally for all your hard work! #givingthanks",
    "tag": [
      {
        "type": "Mention",
        "href": "http://example.org/people/sally",
        "name": "@sally"
      },
      {
        // note that if there's no type it's assumed to be a hashtag
        "id": "http://example.org/tags/givingthanks",
        "name": "#givingthanks"
      }
    ]
 * </pre>
 */

public class APOMention extends APObj {

    public APOMention() {
        put(type, APType.Mention);
    }

    public APOMention(String href, String name) {
        this();
        put(APObj.href, href);
        put(APObj.name, name);
    }

    @Override
    public APOMention put(String key, Object val) {
        super.put(key, val);
        return this;
    }
}
