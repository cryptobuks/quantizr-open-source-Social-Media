
package quanta.response;

import quanta.model.client.RssFeed;
import quanta.response.base.ResponseBase;

public class GetMultiRssResponse extends ResponseBase {
    // JSON of the feed as a string.
    private RssFeed feed;

    public RssFeed getFeed() {
        return this.feed;
    }
    
    public void setFeed(final RssFeed feed) {
        this.feed = feed;
    }

    public GetMultiRssResponse() {
    }
}
